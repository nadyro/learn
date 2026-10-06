package com.kestrel.commerce.payment.domain;

import com.kestrel.commerce.shared.domain.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Remembers which provider events we already handled, so a redelivered webhook is not processed twice. */
@Entity
@Table(name = "payment_webhook_events")
public class ProcessedWebhookEvent extends AssignedIdEntity<String> {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedWebhookEvent() {
        // for JPA
    }

    public ProcessedWebhookEvent(String eventId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    @Override
    public String getId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
