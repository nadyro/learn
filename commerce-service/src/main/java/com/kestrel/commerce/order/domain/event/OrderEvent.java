package com.kestrel.commerce.order.domain.event;

import com.kestrel.commerce.shared.outbox.DomainEvent;
import java.util.UUID;

/** Common contract of the events published on the {@code commerce.order-events.v1} topic. */
public sealed interface OrderEvent extends DomainEvent
        permits OrderPlaced, OrderPaid, OrderCancelled, OrderShipped, OrderDelivered {

    UUID orderId();

    @Override
    default String aggregateType() {
        return "order";
    }

    @Override
    default String aggregateId() {
        return orderId().toString();
    }
}
