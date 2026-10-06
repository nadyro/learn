package com.kestrel.commerce.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kestrel.commerce.catalog.application.CatalogService;
import com.kestrel.commerce.catalog.domain.Product;
import com.kestrel.commerce.customer.application.CustomerService;
import com.kestrel.commerce.customer.domain.Customer;
import com.kestrel.commerce.inventory.application.InventoryService;
import com.kestrel.commerce.inventory.application.StockLine;
import com.kestrel.commerce.order.domain.NewOrderLine;
import com.kestrel.commerce.order.domain.Order;
import com.kestrel.commerce.order.domain.OrderRepository;
import com.kestrel.commerce.order.domain.OrderStatus;
import com.kestrel.commerce.order.domain.ShippingAddress;
import com.kestrel.commerce.order.domain.event.OrderPlaced;
import com.kestrel.commerce.shared.domain.Money;
import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import com.kestrel.commerce.shared.outbox.OutboxWriter;
import com.kestrel.commerce.shared.security.CurrentUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests of the orchestration logic, with the other modules mocked. Database behaviour (locking, constraints) is
 * covered by the integration tests instead: mocks cannot tell you whether a query is correct.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final CurrentUser ALICE = new CurrentUser("alice-sub", "alice", "alice@example.com", "A", "M");
    private static final ShippingAddress ADDRESS =
            new ShippingAddress("Alice Martin", "12 Rue des Alpes", null, "Grenoble", "38000", "FR");

    @Mock
    OrderRepository orders;

    @Mock
    CatalogService catalogService;

    @Mock
    InventoryService inventoryService;

    @Mock
    CustomerService customerService;

    @Mock
    OutboxWriter outbox;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orders,
                catalogService,
                inventoryService,
                customerService,
                outbox,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry());
    }

    @Test
    void placing_an_order_reserves_stock_snapshots_prices_and_records_an_event() {
        Product tent = new Product("TENT-1", "Tent", null, Money.of("100.00", "EUR"));
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(UUID.randomUUID());
        when(customerService.getOrRegister(ALICE)).thenReturn(customer);
        when(catalogService.getPurchasableProducts(List.of(tent.getId()))).thenReturn(Map.of(tent.getId(), tent));
        when(orders.nextOrderNumber()).thenReturn(1042L);

        Order order = orderService.placeOrder(ALICE, command(new PlaceOrderCommand.Item(tent.getId(), 2)));

        assertThat(order.getOrderNumber()).isEqualTo("KO-00001042");
        assertThat(order.getTotal()).isEqualTo(Money.of("200.00", "EUR"));
        assertThat(order.getPlacedAt()).isEqualTo(NOW);
        verify(inventoryService).reserve(List.of(new StockLine(tent.getId(), 2)));
        verify(orders).save(order);
        verify(outbox).append(any(OrderPlaced.class));
    }

    @Test
    void an_order_listing_the_same_product_twice_is_rejected_before_touching_anything() {
        UUID productId = UUID.randomUUID();
        PlaceOrderCommand command =
                command(new PlaceOrderCommand.Item(productId, 1), new PlaceOrderCommand.Item(productId, 2));

        assertThatThrownBy(() -> orderService.placeOrder(ALICE, command))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.DUPLICATE_ORDER_LINE);
        verifyNoInteractions(customerService, inventoryService, orders, outbox);
    }

    @Test
    void a_redelivered_payment_confirmation_is_ignored() {
        Order order = pendingOrder();
        order.markPaid("pay_1", NOW);
        when(orders.findWithLinesById(order.getId())).thenReturn(Optional.of(order));

        orderService.confirmPayment(order.getId(), "pay_1", order.getTotal());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(outbox);
    }

    @Test
    void a_second_payment_for_an_already_paid_order_is_rejected() {
        Order order = pendingOrder();
        order.markPaid("pay_1", NOW);
        when(orders.findWithLinesById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmPayment(order.getId(), "pay_2", order.getTotal()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATE);
    }

    @Test
    void a_payment_that_does_not_match_the_total_is_rejected() {
        Order order = pendingOrder();
        when(orders.findWithLinesById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmPayment(order.getId(), "pay_1", Money.of("1.00", "EUR")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verifyNoInteractions(outbox);
    }

    @Test
    void an_unknown_order_cannot_be_paid() {
        UUID unknown = UUID.randomUUID();
        when(orders.findWithLinesById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmPayment(unknown, "pay_1", Money.of("1.00", "EUR")))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    private static PlaceOrderCommand command(PlaceOrderCommand.Item... items) {
        return new PlaceOrderCommand(List.of(items), ADDRESS);
    }

    private static Order pendingOrder() {
        NewOrderLine line = new NewOrderLine(UUID.randomUUID(), "TENT-1", "Tent", Money.of("100.00", "EUR"), 1);
        return Order.place("KO-00000001", UUID.randomUUID(), ADDRESS, List.of(line), NOW);
    }
}
