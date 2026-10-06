package com.kestrel.commerce.order.domain.event;

import com.kestrel.commerce.order.domain.CancellationReason;
import com.kestrel.commerce.order.domain.Order;
import java.time.Instant;
import java.util.UUID;

public record OrderCancelled(
        UUID orderId, String orderNumber, UUID customerId, CancellationReason reason, Instant cancelledAt)
        implements OrderEvent {

    public static OrderCancelled from(Order order) {
        return new OrderCancelled(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getCancellationReason(),
                order.getCancelledAt());
    }

    @Override
    public String eventType() {
        return "order.cancelled";
    }
}
