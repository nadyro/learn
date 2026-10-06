package com.kestrel.commerce.inventory.domain;

import com.kestrel.commerce.shared.domain.AuditableEntity;
import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;

/**
 * Stock of one product in our (single) warehouse.
 *
 * <ul>
 *   <li>{@code onHand}: units physically in the warehouse.
 *   <li>{@code reserved}: units promised to orders that have not shipped yet.
 *   <li>{@code available = onHand - reserved}: what can still be sold.
 * </ul>
 *
 * Lifecycle of a unit: order placed → {@link #reserve}; order cancelled → {@link #release}; order shipped →
 * {@link #fulfil} (the unit leaves the warehouse).
 */
@Entity
@Table(name = "inventory_items")
public class InventoryItem extends AuditableEntity {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Column(name = "reserved", nullable = false)
    private int reserved;

    protected InventoryItem() {
        // for JPA
    }

    public InventoryItem(UUID productId, int initialOnHand) {
        if (initialOnHand < 0) {
            throw new IllegalArgumentException("initial stock must not be negative");
        }
        this.productId = productId;
        this.onHand = initialOnHand;
        this.reserved = 0;
    }

    public int available() {
        return onHand - reserved;
    }

    public boolean canReserve(int quantity) {
        return available() >= quantity;
    }

    public void reserve(int quantity) {
        requirePositive(quantity);
        if (!canReserve(quantity)) {
            throw new IllegalStateException("Cannot reserve " + quantity + " units of " + productId + ", only "
                    + available() + " available. Check canReserve() first.");
        }
        reserved += quantity;
    }

    public void release(int quantity) {
        requirePositive(quantity);
        if (quantity > reserved) {
            throw new IllegalStateException(
                    "Cannot release " + quantity + " units of " + productId + ", only " + reserved + " reserved");
        }
        reserved -= quantity;
    }

    public void fulfil(int quantity) {
        requirePositive(quantity);
        if (quantity > reserved) {
            throw new IllegalStateException(
                    "Cannot fulfil " + quantity + " units of " + productId + ", only " + reserved + " reserved");
        }
        reserved -= quantity;
        onHand -= quantity;
    }

    /** Manual correction by the warehouse team (restock, damaged goods, inventory count...). */
    public void adjust(int delta) {
        if (delta == 0) {
            throw new DomainException(ErrorCode.INVALID_STOCK_ADJUSTMENT, "A stock adjustment cannot be zero.");
        }
        int newOnHand = onHand + delta;
        if (newOnHand < reserved) {
            throw new DomainException(
                    ErrorCode.INVALID_STOCK_ADJUSTMENT,
                    "Adjustment would leave " + newOnHand + " units on hand while " + reserved
                            + " are reserved for open orders.",
                    Map.of("onHand", onHand, "reserved", reserved, "delta", delta));
        }
        onHand = newOnHand;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, got: " + quantity);
        }
    }

    public UUID getProductId() {
        return productId;
    }

    public int getOnHand() {
        return onHand;
    }

    public int getReserved() {
        return reserved;
    }
}
