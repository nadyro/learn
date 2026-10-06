package com.kestrel.commerce.shared.outbox;

import com.kestrel.commerce.shared.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records domain events in the {@code outbox_events} table.
 *
 * <p>This is the "transactional outbox" pattern: the event is written in the <b>same database transaction</b> as the
 * business change (e.g. the new order). Either both are committed or neither is, so we can never publish an event for
 * an order that was rolled back, or lose the event of an order that was saved. {@link OutboxRelay} then publishes the
 * rows to Kafka asynchronously.
 *
 * <p>{@link Propagation#MANDATORY} makes calling this method outside a transaction a programming error that fails
 * loudly instead of silently breaking the guarantee.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final String producer;

    public OutboxWriter(
            OutboxEventRepository repository,
            JsonMapper jsonMapper,
            Clock clock,
            @Value("${spring.application.name}") String producer) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.producer = producer;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        UUID eventId = Ids.newId();
        Instant occurredAt = clock.instant();
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                event.eventType(),
                event.eventVersion(),
                occurredAt,
                event.aggregateType(),
                event.aggregateId(),
                producer,
                event);
        String payload = jsonMapper.writeValueAsString(envelope);
        repository.save(new OutboxEvent(
                eventId, event.aggregateType(), event.aggregateId(), event.eventType(), payload, occurredAt));
    }
}
