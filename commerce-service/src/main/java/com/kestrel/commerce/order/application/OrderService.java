package com.kestrel.commerce.order.application;

import com.kestrel.commerce.catalog.application.CatalogService;
import com.kestrel.commerce.catalog.domain.Product;
import com.kestrel.commerce.customer.application.CustomerService;
import com.kestrel.commerce.customer.domain.Customer;
import com.kestrel.commerce.inventory.application.InventoryService;
import com.kestrel.commerce.inventory.application.StockLine;
import com.kestrel.commerce.order.domain.CancellationReason;
import com.kestrel.commerce.order.domain.NewOrderLine;
import com.kestrel.commerce.order.domain.Order;
import com.kestrel.commerce.order.domain.OrderRepository;
import com.kestrel.commerce.order.domain.OrderSpecifications;
import com.kestrel.commerce.order.domain.OrderStatus;
import com.kestrel.commerce.order.domain.event.OrderCancelled;
import com.kestrel.commerce.order.domain.event.OrderDelivered;
import com.kestrel.commerce.order.domain.event.OrderPaid;
import com.kestrel.commerce.order.domain.event.OrderPlaced;
import com.kestrel.commerce.order.domain.event.OrderShipped;
import com.kestrel.commerce.shared.domain.Money;
import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import com.kestrel.commerce.shared.error.NotFoundException;
import com.kestrel.commerce.shared.outbox.OutboxWriter;
import com.kestrel.commerce.shared.security.CurrentUser;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the order module.
 *
 * <p>Each public method is one transaction: stock, order and outbox event are committed together or not at all.
 */
@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String ORDER_NUMBER_FORMAT = "KO-%08d";

    private final OrderRepository orders;
    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final OutboxWriter outbox;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public OrderService(
            OrderRepository orders,
            CatalogService catalogService,
            InventoryService inventoryService,
            CustomerService customerService,
            OutboxWriter outbox,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.orders = orders;
        this.catalogService = catalogService;
        this.inventoryService = inventoryService;
        this.customerService = customerService;
        this.outbox = outbox;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Customer use cases
    // ---------------------------------------------------------------------------------------------------------------

    public Order placeOrder(CurrentUser user, PlaceOrderCommand command) {
        rejectDuplicateProducts(command);
        Customer customer = customerService.getOrRegister(user);

        List<UUID> productIds =
                command.items().stream().map(PlaceOrderCommand.Item::productId).toList();
        Map<UUID, Product> products = catalogService.getPurchasableProducts(productIds);

        inventoryService.reserve(command.items().stream()
                .map(item -> new StockLine(item.productId(), item.quantity()))
                .toList());

        List<NewOrderLine> lines = command.items().stream()
                .map(item -> {
                    Product product = products.get(item.productId());
                    return new NewOrderLine(
                            product.getId(), product.getSku(), product.getName(), product.getPrice(), item.quantity());
                })
                .toList();

        Order order = Order.place(
                String.format(ORDER_NUMBER_FORMAT, orders.nextOrderNumber()),
                customer.getId(),
                command.shippingAddress(),
                lines,
                clock.instant());
        orders.save(order);
        outbox.append(OrderPlaced.from(order));

        meterRegistry.counter("commerce.orders.placed").increment();
        log.info(
                "Order {} placed by customer {} ({} lines, total {})",
                order.getOrderNumber(),
                customer.getId(),
                lines.size(),
                order.getTotal());
        return order;
    }

    @Transactional(readOnly = true)
    public Order getCustomerOrder(CurrentUser user, UUID orderId) {
        // A customer asking for someone else's order gets a 404, not a 403: we don't reveal that the order exists.
        return customerService
                .findBySubject(user.subject())
                .flatMap(customer -> orders.findWithLinesByIdAndCustomerId(orderId, customer.getId()))
                .orElseThrow(() -> orderNotFound(orderId));
    }

    @Transactional(readOnly = true)
    public Page<Order> listCustomerOrders(CurrentUser user, OrderStatus status, Pageable pageable) {
        return customerService
                .findBySubject(user.subject())
                .map(customer -> status == null
                        ? orders.findByCustomerId(customer.getId(), pageable)
                        : orders.findByCustomerIdAndStatus(customer.getId(), status, pageable))
                .orElseGet(() -> Page.empty(pageable));
    }

    public Order cancelCustomerOrder(CurrentUser user, UUID orderId) {
        Order order = getCustomerOrder(user, orderId);
        return cancel(order, CancellationReason.CUSTOMER_REQUEST);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Back-office use cases
    // ---------------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Order> searchOrders(OrderStatus status, UUID customerId, Pageable pageable) {
        List<Specification<Order>> criteria = new ArrayList<>();
        if (status != null) {
            criteria.add(OrderSpecifications.hasStatus(status));
        }
        if (customerId != null) {
            criteria.add(OrderSpecifications.belongsToCustomer(customerId));
        }
        return orders.findAll(Specification.allOf(criteria), pageable);
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orders.findWithLinesById(orderId).orElseThrow(() -> orderNotFound(orderId));
    }

    public Order cancelOrder(UUID orderId) {
        return cancel(getOrder(orderId), CancellationReason.BACK_OFFICE);
    }

    public Order shipOrder(UUID orderId, String carrier, String trackingNumber) {
        Order order = getOrder(orderId);
        order.ship(carrier, trackingNumber, clock.instant());
        inventoryService.fulfil(stockLines(order));
        outbox.append(OrderShipped.from(order));
        log.info("Order {} shipped with {} ({})", order.getOrderNumber(), carrier, trackingNumber);
        return order;
    }

    public Order markDelivered(UUID orderId) {
        Order order = getOrder(orderId);
        order.markDelivered(clock.instant());
        outbox.append(OrderDelivered.from(order));
        log.info("Order {} delivered", order.getOrderNumber());
        return order;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Payment use cases (called by the payment module)
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Records a successful payment. Safe to call several times for the same payment: payment providers deliver
     * webhooks at least once.
     */
    public Order confirmPayment(UUID orderId, String paymentReference, Money amountPaid) {
        Order order = getOrder(orderId);
        if (order.getStatus() == OrderStatus.PAID && Objects.equals(order.getPaymentReference(), paymentReference)) {
            log.info("Payment {} already recorded for order {}", paymentReference, order.getOrderNumber());
            return order;
        }
        if (!order.getTotal().equals(amountPaid)) {
            throw new DomainException(
                    ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "Payment " + paymentReference + " of " + amountPaid + " does not match order total "
                            + order.getTotal() + ".",
                    Map.of(
                            "orderId", orderId,
                            "expected", order.getTotal().toString(),
                            "received", amountPaid.toString()));
        }
        order.markPaid(paymentReference, clock.instant());
        outbox.append(OrderPaid.from(order));
        meterRegistry.counter("commerce.orders.paid").increment();
        log.info("Order {} paid (payment {})", order.getOrderNumber(), paymentReference);
        return order;
    }

    // ---------------------------------------------------------------------------------------------------------------

    private Order cancel(Order order, CancellationReason reason) {
        order.cancel(reason, clock.instant());
        inventoryService.release(stockLines(order));
        outbox.append(OrderCancelled.from(order));
        meterRegistry
                .counter("commerce.orders.cancelled", "reason", reason.name())
                .increment();
        log.info("Order {} cancelled ({})", order.getOrderNumber(), reason);
        return order;
    }

    private static List<StockLine> stockLines(Order order) {
        return order.getLines().stream()
                .map(line -> new StockLine(line.getProductId(), line.getQuantity()))
                .toList();
    }

    private static void rejectDuplicateProducts(PlaceOrderCommand command) {
        Set<UUID> seen = new HashSet<>();
        for (PlaceOrderCommand.Item item : command.items()) {
            if (!seen.add(item.productId())) {
                throw new DomainException(
                        ErrorCode.DUPLICATE_ORDER_LINE,
                        "Product " + item.productId() + " appears more than once. Use the quantity instead.",
                        Map.of("productId", item.productId()));
            }
        }
    }

    private static NotFoundException orderNotFound(UUID orderId) {
        return new NotFoundException(ErrorCode.ORDER_NOT_FOUND, "Order", orderId);
    }
}
