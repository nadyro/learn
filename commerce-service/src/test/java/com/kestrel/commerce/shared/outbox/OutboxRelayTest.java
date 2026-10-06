package com.kestrel.commerce.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final int BATCH_SIZE = 2;

    @Mock
    OutboxEventRepository repository;

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    TransactionTemplate transactionTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        // Run the "transaction" callback inline: the unit under test is the relay logic, not Spring's transactions.
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        OutboxProperties properties = new OutboxProperties(
                Map.of("order", "commerce.order-events.v1"),
                false,
                new OutboxProperties.Relay(true, Duration.ofSeconds(1), BATCH_SIZE, Duration.ofSeconds(1)));
        relay = new OutboxRelay(
                repository,
                kafkaTemplate,
                transactionTemplate,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry());
    }

    @Test
    void events_are_published_keyed_by_aggregate_and_marked_as_published() {
        OutboxEvent event = event("order-1");
        when(repository.lockNextBatch(BATCH_SIZE)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(sent());

        relay.publishPendingEvents();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(record.capture());
        assertThat(record.getValue().topic()).isEqualTo("commerce.order-events.v1");
        assertThat(record.getValue().key()).isEqualTo("order-1");
        assertThat(record.getValue().value()).isEqualTo(event.getPayload());
        assertThat(new String(
                        record.getValue().headers().lastHeader("event-type").value(), StandardCharsets.UTF_8))
                .isEqualTo("order.placed");
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    @Test
    void the_batch_stops_at_the_first_failure_to_preserve_ordering() {
        OutboxEvent first = event("order-1");
        OutboxEvent second = event("order-1");
        when(repository.lockNextBatch(BATCH_SIZE)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        relay.publishPendingEvents();

        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        assertThat(first.getPublishedAt()).isNull();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("broker down");
        assertThat(second.getAttempts()).isZero();
    }

    @Test
    void full_batches_are_drained_in_the_same_run() {
        when(repository.lockNextBatch(BATCH_SIZE))
                .thenReturn(List.of(event("a"), event("b")))
                .thenReturn(List.of(event("c")));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(sent());

        relay.publishPendingEvents();

        verify(repository, times(2)).lockNextBatch(BATCH_SIZE);
        verify(kafkaTemplate, times(3)).send(any(ProducerRecord.class));
    }

    @Test
    void nothing_is_sent_when_the_outbox_is_empty() {
        when(repository.lockNextBatch(BATCH_SIZE)).thenReturn(List.of());

        relay.publishPendingEvents();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    private static OutboxEvent event(String aggregateId) {
        return new OutboxEvent(UUID.randomUUID(), "order", aggregateId, "order.placed", "{\"x\":1}", NOW);
    }

    private static CompletableFuture<SendResult<String, String>> sent() {
        return CompletableFuture.completedFuture(null);
    }
}
