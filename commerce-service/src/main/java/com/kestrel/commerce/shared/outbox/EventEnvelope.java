package com.kestrel.commerce.shared.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * The JSON message published on Kafka. Metadata is identical for every event so consumers can route, deduplicate
 * (using {@code eventId}) and order events without knowing their payload.
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String aggregateType,
        String aggregateId,
        String producer,
        Object data) {}
