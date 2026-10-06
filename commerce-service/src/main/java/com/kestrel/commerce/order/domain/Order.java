package com.kestrel.commerce.order.domain;

import com.kestrel.commerce.shared.domain.AuditableEntity;
import com.kestrel.commerce.shared.domain.Ids;
import com.kestrel.commerce.shared.domain.Money;
import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A customer order: the aggregate root of the order module.
 *
 * <p>All changes go through methods that enforce the {@link OrderStatus} state machine, so an order can never be, for
 * instance, shipped before it is paid. The customer is referenced by ID only (not a JPA relation): modules reference
 * each other's aggregates by identity, which keeps them decoupled and avoids loading half the database by accident.
 */
@Entity
@Table(name = "orders")
public class Order extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "order_number", nullable = false, updatable = false)
    private String orderNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "currency", nullable = false, updatable = false)
    private String currency;

    @Column(name = "total_amount", nullable = false, updatable = false)
    private BigDecimal totalAmount;

    @Embedded
    private ShippingAddress shippingAddress;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber asc")
    private List<OrderLine> lines = new ArrayList<>();

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "carrier")
    private String carrier;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason")
    private CancellationReason cancellationReason;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Order() {
        // for JPA
    }

    public static Order place(
            String orderNumber,
            UUID customerId,
            ShippingAddress shippingAddress,
            List<NewOrderLine> newLines,
            Instant placedAt) {
        if (newLines.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one line");
        }
        String currency = newLines.getFirst().unitPrice().currency();
        if (newLines.stream().anyMatch(line -> !line.unitPrice().currency().equals(currency))) {
            throw new DomainException(
                    ErrorCode.MIXED_CURRENCIES, "All products of an order must be priced in the same currency.");
        }

        Order order = new Order();
        order.id = Ids.newId();
        order.orderNumber = Objects.requireNonNull(orderNumber);
        order.customerId = Objects.requireNonNull(customerId);
        order.shippingAddress = Objects.requireNonNull(shippingAddress);
        order.currency = currency;
        order.status = OrderStatus.PENDING_PAYMENT;
        order.placedAt = placedAt;

        Money total = Money.zero(currency);
        int lineNumber = 1;
        for (NewOrderLine newLine : newLines) {
            OrderLine line = new OrderLine(order, lineNumber++, newLine);
            order.lines.add(line);
            total = total.add(line.getLineTotal());
        }
        order.totalAmount = total.amount();
        return order;
    }

    public void markPaid(String paymentReference, Instant when) {
        transitionTo(OrderStatus.PAID);
        this.paymentReference = Objects.requireNonNull(paymentReference);
        this.paidAt = when;
    }

    public void ship(String carrier, String trackingNumber, Instant when) {
        transitionTo(OrderStatus.SHIPPED);
        this.carrier = Objects.requireNonNull(carrier);
        this.trackingNumber = Objects.requireNonNull(trackingNumber);
        this.shippedAt = when;
    }

    public void markDelivered(Instant when) {
        transitionTo(OrderStatus.DELIVERED);
        this.deliveredAt = when;
    }

    public void cancel(CancellationReason reason, Instant when) {
        transitionTo(OrderStatus.CANCELLED);
        this.cancellationReason = Objects.requireNonNull(reason);
        this.cancelledAt = when;
    }

    private void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new DomainException(
                    ErrorCode.INVALID_ORDER_STATE,
                    "Order " + orderNumber + " is " + status + " and cannot become " + target + ".",
                    Map.of("currentStatus", status, "targetStatus", target));
        }
        this.status = target;
    }

    public UUID getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public Money getTotal() {
        return new Money(totalAmount, currency);
    }

    public ShippingAddress getShippingAddress() {
        return shippingAddress;
    }

    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public CancellationReason getCancellationReason() {
        return cancellationReason;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Order order && id.equals(order.getId()));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
