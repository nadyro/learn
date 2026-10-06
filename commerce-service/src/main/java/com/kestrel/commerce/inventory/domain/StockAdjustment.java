package com.kestrel.commerce.inventory.domain;

import com.kestrel.commerce.shared.domain.AssignedIdEntity;
import com.kestrel.commerce.shared.domain.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record of a manual stock change: who changed what, when and why.
 *
 * <p>Finance and the warehouse rely on this history to reconcile stock, so rows are never updated or deleted.
 */
@Entity
@Table(name = "stock_adjustments")
public class StockAdjustment extends AssignedIdEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "delta", nullable = false, updatable = false)
    private int delta;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false)
    private AdjustmentReason reason;

    @Column(name = "note", updatable = false)
    private String note;

    @Column(name = "performed_by", nullable = false, updatable = false)
    private String performedBy;

    @Column(name = "on_hand_after", nullable = false, updatable = false)
    private int onHandAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockAdjustment() {
        // for JPA
    }

    public StockAdjustment(
            UUID productId,
            int delta,
            AdjustmentReason reason,
            String note,
            String performedBy,
            int onHandAfter,
            Instant createdAt) {
        this.id = Ids.newId();
        this.productId = productId;
        this.delta = delta;
        this.reason = reason;
        this.note = note;
        this.performedBy = performedBy;
        this.onHandAfter = onHandAfter;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getDelta() {
        return delta;
    }

    public AdjustmentReason getReason() {
        return reason;
    }

    public String getNote() {
        return note;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public int getOnHandAfter() {
        return onHandAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
