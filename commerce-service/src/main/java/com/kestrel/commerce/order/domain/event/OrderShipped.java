package com.kestrel.commerce.order.domain.event;

import com.kestrel.commerce.order.domain.Order;
import java.time.Instant;
import java.util.UUID;

public record OrderShipped(
        UUID orderId, String orderNumber, UUID customerId, String carrier, String trackingNumber, Instant shippedAt)
        implements OrderEvent {

    public static OrderShipped from(Order order) {
        return new OrderShipped(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getCarrier(),
                order.getTrackingNumber(),
                order.getShippedAt());
    }

    @Override
    public String eventType() {
        return "order.shipped";
    }
}
