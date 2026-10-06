package com.kestrel.commerce.shared.outbox;

/**
 * An event that is published to other systems through the transactional outbox.
 *
 * <p>Implementations are serialised to JSON as the {@code data} part of an {@link EventEnvelope}. They form a public
 * contract consumed by other teams (analytics, notifications, warehouse...): follow the rules in
 * {@code docs/events.md} before changing one.
 */
public interface DomainEvent {

    /** Kind of aggregate the event is about, e.g. {@code order}. Determines the Kafka topic. */
    String aggregateType();

    /** ID of the aggregate. Used as the Kafka message key, which guarantees ordering per aggregate. */
    String aggregateId();

    /** Event name in {@code <aggregate>.<past-tense-verb>} form, e.g. {@code order.placed}. */
    String eventType();

    /** Schema version of {@code data}. Bump it on breaking changes (and talk to consumers first). */
    default int eventVersion() {
        return 1;
    }
}
