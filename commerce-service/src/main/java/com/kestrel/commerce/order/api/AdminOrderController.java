package com.kestrel.commerce.order.api;

import com.kestrel.commerce.order.application.OrderService;
import com.kestrel.commerce.order.domain.OrderStatus;
import com.kestrel.commerce.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Order operations for customer support and the warehouse. Requires the {@code admin} role. */
@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Orders (admin)", description = "Search orders and drive fulfilment")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "Search orders", description = "All filters are optional. Newest first.")
    public PageResponse<OrderSummaryResponse> searchOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "placedAt", "id"));
        return PageResponse.from(
                orderService.searchOrders(status, customerId, pageRequest), OrderSummaryResponse::from);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get an order")
    public OrderResponse getOrder(@PathVariable UUID orderId) {
        return OrderResponse.from(orderService.getOrder(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an unpaid order on behalf of the customer")
    public OrderResponse cancelOrder(@PathVariable UUID orderId) {
        return OrderResponse.from(orderService.cancelOrder(orderId));
    }

    @PostMapping("/{orderId}/shipment")
    @Operation(summary = "Mark a paid order as shipped", description = "Removes the units from the warehouse stock.")
    public OrderResponse shipOrder(@PathVariable UUID orderId, @Valid @RequestBody ShipOrderRequest request) {
        return OrderResponse.from(orderService.shipOrder(orderId, request.carrier(), request.trackingNumber()));
    }

    @PostMapping("/{orderId}/delivery")
    @Operation(summary = "Mark a shipped order as delivered")
    public OrderResponse markDelivered(@PathVariable UUID orderId) {
        return OrderResponse.from(orderService.markDelivered(orderId));
    }
}
