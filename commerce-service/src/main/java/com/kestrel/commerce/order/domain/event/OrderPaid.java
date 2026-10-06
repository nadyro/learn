package com.kestrel.commerce.order.domain.event;

import com.kestrel.commerce.order.domain.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderPaid(
        UUID orderId,
        String orderNumber,
        UUID customerId,
        String paymentReference,
        BigDecimal totalAmount,
        String currency,
        Instant paidAt)
        implements OrderEvent {

    public static OrderPaid from(Order order) {
        return new OrderPaid(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getPaymentReference(),
                order.getTotal().amount(),
                order.getCurrency(),
                order.getPaidAt());
    }

    @Override
    public String eventType() {
        return "order.paid";
    }
}
