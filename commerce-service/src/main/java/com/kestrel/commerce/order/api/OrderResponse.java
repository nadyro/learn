package com.kestrel.commerce.order.api;

import com.kestrel.commerce.order.domain.CancellationReason;
import com.kestrel.commerce.order.domain.Order;
import com.kestrel.commerce.order.domain.OrderLine;
import com.kestrel.commerce.order.domain.OrderStatus;
import com.kestrel.commerce.shared.web.MoneyDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderNumber,
        OrderStatus status,
        UUID customerId,
        List<Line> lines,
        MoneyDto total,
        ShippingAddressDto shippingAddress,
        String paymentReference,
        String carrier,
        String trackingNumber,
        CancellationReason cancellationReason,
        Instant placedAt,
        Instant paidAt,
        Instant shippedAt,
        Instant deliveredAt,
        Instant cancelledAt) {

    public record Line(
            int lineNumber,
            UUID productId,
            String sku,
            String productName,
            MoneyDto unitPrice,
            int quantity,
            MoneyDto lineTotal) {

        static Line from(OrderLine line) {
            return new Line(
                    line.getLineNumber(),
                    line.getProductId(),
                    line.getSku(),
                    line.getProductName(),
                    MoneyDto.from(line.getUnitPrice()),
                    line.getQuantity(),
                    MoneyDto.from(line.getLineTotal()));
        }
    }

    static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getCustomerId(),
                order.getLines().stream().map(Line::from).toList(),
                MoneyDto.from(order.getTotal()),
                ShippingAddressDto.from(order.getShippingAddress()),
                order.getPaymentReference(),
                order.getCarrier(),
                order.getTrackingNumber(),
                order.getCancellationReason(),
                order.getPlacedAt(),
                order.getPaidAt(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getCancelledAt());
    }
}
