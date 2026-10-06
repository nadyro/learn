package com.kestrel.commerce.order.domain;

/**
 * Lifecycle of an order.
 *
 * <pre>
 *   PENDING_PAYMENT ──paid──▶ PAID ──shipped──▶ SHIPPED ──delivered──▶ DELIVERED
 *         │
 *         └──cancelled──▶ CANCELLED
 * </pre>
 *
 * Paid orders cannot be cancelled yet: that requires refunds, which are not implemented.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING_PAYMENT -> target == PAID || target == CANCELLED;
            case PAID -> target == SHIPPED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
