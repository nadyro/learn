package com.kestrel.commerce.order.api;

import com.kestrel.commerce.order.domain.Order;
import com.kestrel.commerce.order.domain.OrderStatus;
import com.kestrel.commerce.shared.web.MoneyDto;
import java.time.Instant;
import java.util.UUID;

/** Lightweight representation for lists. Does not include lines, which would need an extra query per order. */
public record OrderSummaryResponse(
        UUID id, String orderNumber, OrderStatus status, UUID customerId, MoneyDto total, Instant placedAt) {

    static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getCustomerId(),
                MoneyDto.from(order.getTotal()),
                order.getPlacedAt());
    }
}
