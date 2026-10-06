package com.kestrel.commerce.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.kestrel.commerce.shared.domain.Money;
import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final ShippingAddress ADDRESS =
            new ShippingAddress("Alice Martin", "12 Rue des Alpes", null, "Grenoble", "38000", "FR");

    @Test
    void a_new_order_waits_for_payment_and_snapshots_the_products() {
        Order order = Order.place(
                "KO-00000001",
                UUID.randomUUID(),
                ADDRESS,
                List.of(line("TENT", "349.00", 1), line("LAMP", "49.90", 2)),
                NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getPlacedAt()).isEqualTo(NOW);
        assertThat(order.getTotal()).isEqualTo(Money.of("448.80", "EUR"));
        assertThat(order.getLines())
                .extracting(OrderLine::getLineNumber, OrderLine::getSku, OrderLine::getLineTotal)
                .containsExactly(
                        tuple(1, "TENT", Money.of("349.00", "EUR")), tuple(2, "LAMP", Money.of("99.80", "EUR")));
    }

    @Test
    void products_priced_in_different_currencies_cannot_be_ordered_together() {
        List<NewOrderLine> lines = List.of(
                line("TENT", "349.00", 1),
                new NewOrderLine(UUID.randomUUID(), "LAMP", "Lamp", Money.of("49.90", "USD"), 1));

        assertThatThrownBy(() -> Order.place("KO-1", UUID.randomUUID(), ADDRESS, lines, NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.MIXED_CURRENCIES);
    }

    @Test
    void the_full_lifecycle_records_when_each_step_happened() {
        Order order = newOrder();

        order.markPaid("pay_1", NOW.plusSeconds(60));
        order.ship("Colissimo", "6A1", NOW.plusSeconds(3600));
        order.markDelivered(NOW.plusSeconds(86_400));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getPaymentReference()).isEqualTo("pay_1");
        assertThat(order.getPaidAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(order.getShippedAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(order.getDeliveredAt()).isEqualTo(NOW.plusSeconds(86_400));
    }

    @Test
    void an_unpaid_order_cannot_be_shipped() {
        Order order = newOrder();

        assertThatThrownBy(() -> order.ship("Colissimo", "6A1", NOW))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("PENDING_PAYMENT")
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void a_cancelled_order_keeps_the_reason() {
        Order order = newOrder();

        order.cancel(CancellationReason.CUSTOMER_REQUEST, NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.CUSTOMER_REQUEST);
        assertThat(order.getCancelledAt()).isEqualTo(NOW);
    }

    @Test
    void a_paid_order_cannot_be_cancelled_until_refunds_exist() {
        Order order = newOrder();
        order.markPaid("pay_1", NOW);

        assertThatThrownBy(() -> order.cancel(CancellationReason.CUSTOMER_REQUEST, NOW))
                .isInstanceOf(DomainException.class);
    }

    private static Order newOrder() {
        return Order.place("KO-00000001", UUID.randomUUID(), ADDRESS, List.of(line("TENT", "349.00", 1)), NOW);
    }

    private static NewOrderLine line(String sku, String price, int quantity) {
        return new NewOrderLine(UUID.randomUUID(), sku, sku + " name", Money.of(price, "EUR"), quantity);
    }
}
