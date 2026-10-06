package com.kestrel.commerce.order.domain.event;

import com.kestrel.commerce.order.domain.Order;
import java.time.Instant;
import java.util.UUID;

public record OrderDelivered(UUID orderId, String orderNumber, UUID customerId, Instant deliveredAt)
        implements OrderEvent {

    public static OrderDelivered from(Order order) {
        return new OrderDelivered(order.getId(), order.getOrderNumber(), order.getCustomerId(), order.getDeliveredAt());
    }

    @Override
    public String eventType() {
        return "order.delivered";
    }
}
