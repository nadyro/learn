package com.kestrel.commerce.shared.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes outbox rows to Kafka.
 *
 * <p>Delivery is <b>at-least-once</b>: if the process dies after Kafka acknowledged a message but before the row was
 * marked as published, the message is sent again on the next run. Consumers must therefore be idempotent, which they
 * can do using the {@code eventId} of the envelope.
 *
 * <p>Ordering: rows are published in {@code occurred_at} order and the Kafka key is the aggregate ID, so all events of
 * one order land on the same partition in the order they happened. On the first failure the batch stops, so a later
 * event is never published before an earlier one.
 */
@Component
@ConditionalOnBooleanProperty(name = "commerce.outbox.relay.enabled", matchIfMissing = true)
public class OutboxRelay {

    static final String HEADER_EVENT_ID = "event-id";
    static final String HEADER_EVENT_TYPE = "event-type";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties properties;
    private final Clock clock;
    private final Counter publishedCounter;
    private final Counter failureCounter;

    public OutboxRelay(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            TransactionTemplate transactionTemplate,
            OutboxProperties properties,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
        this.publishedCounter = Counter.builder("commerce.outbox.events.published")
                .description("Outbox events successfully published to Kafka")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("commerce.outbox.events.publish.failures")
                .description("Failed attempts to publish an outbox event to Kafka")
                .register(meterRegistry);
        Gauge.builder("commerce.outbox.events.pending", repository, OutboxEventRepository::countByPublishedAtIsNull)
                .description("Outbox events waiting to be published. Should stay close to zero.")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${commerce.outbox.relay.interval:PT1S}")
    public void publishPendingEvents() {
        int batchSize = properties.relay().batchSize();
        BatchResult result;
        do {
            result = transactionTemplate.execute(status -> publishNextBatch(batchSize));
        } while (result != null && result.shouldContinue(batchSize));
    }

    private BatchResult publishNextBatch(int batchSize) {
        List<OutboxEvent> batch = repository.lockNextBatch(batchSize);
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                send(event);
                event.markPublished(clock.instant());
                publishedCounter.increment();
                published++;
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                event.markFailed(e.getMessage());
                failureCounter.increment();
                logFailure(event, e);
                return new BatchResult(batch.size(), published, true);
            }
        }
        if (published > 0) {
            log.debug("Published {} outbox events", published);
        }
        return new BatchResult(batch.size(), published, false);
    }

    private static void logFailure(OutboxEvent event, Exception e) {
        // Kafka outages make every run fail: log the stack trace once, then a one-liner, to avoid flooding the logs.
        if (event.getAttempts() == 1) {
            log.warn("Could not publish outbox event {} ({}), will retry", event.getId(), event.getEventType(), e);
        } else {
            log.warn(
                    "Still cannot publish outbox event {} ({}) after {} attempts: {}",
                    event.getId(),
                    event.getEventType(),
                    event.getAttempts(),
                    e.getMessage());
        }
    }

    private void send(OutboxEvent event) throws InterruptedException, ExecutionException, TimeoutException {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                properties.topicFor(event.getAggregateType()), event.getAggregateId(), event.getPayload());
        record.headers().add(HEADER_EVENT_ID, event.getId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(HEADER_EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).get(properties.relay().sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private record BatchResult(int fetched, int published, boolean failed) {

        /** Keep draining while batches come back full and nothing failed. */
        boolean shouldContinue(int batchSize) {
            return !failed && fetched == batchSize && published == fetched;
        }
    }
}
