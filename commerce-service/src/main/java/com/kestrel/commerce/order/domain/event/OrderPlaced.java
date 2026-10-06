package com.kestrel.commerce.order.domain.event;

import com.kestrel.commerce.order.domain.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderPlaced(
        UUID orderId,
        String orderNumber,
        UUID customerId,
        List<Line> lines,
        BigDecimal totalAmount,
        String currency,
        String shippingCountryCode,
        Instant placedAt)
        implements OrderEvent {

    public record Line(UUID productId, String sku, int quantity, BigDecimal unitPrice) {}

    public static OrderPlaced from(Order order) {
        List<Line> lines = order.getLines().stream()
                .map(line -> new Line(
                        line.getProductId(),
                        line.getSku(),
                        line.getQuantity(),
                        line.getUnitPrice().amount()))
                .toList();
        return new OrderPlaced(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                lines,
                order.getTotal().amount(),
                order.getCurrency(),
                order.getShippingAddress().countryCode(),
                order.getPlacedAt());
    }

    @Override
    public String eventType() {
        return "order.placed";
    }
}
