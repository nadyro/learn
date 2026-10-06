package com.kestrel.commerce.order.api;

import com.kestrel.commerce.order.application.OrderService;
import com.kestrel.commerce.order.domain.OrderStatus;
import com.kestrel.commerce.shared.idempotency.IdempotencyService;
import com.kestrel.commerce.shared.idempotency.IdempotentResult;
import com.kestrel.commerce.shared.security.CurrentUser;
import com.kestrel.commerce.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Orders of the authenticated customer. */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Place and follow your orders")
public class OrderController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    public OrderController(OrderService orderService, IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    @Operation(
            summary = "Place an order",
            description = "Reserves the stock and creates the order in PENDING_PAYMENT status. Requires an"
                    + " Idempotency-Key header (e.g. a UUID): retrying with the same key returns the original"
                    + " order instead of creating a new one.")
    public ResponseEntity<OrderResponse> placeOrder(
            @Parameter(
                            description = "Unique key per order attempt, reused when retrying",
                            example = "3f1c9a3e-0d5b-4c1e-9b7a-2f7c1d8e6a10")
                    @RequestHeader(IDEMPOTENCY_KEY_HEADER)
                    @Pattern(regexp = "[A-Za-z0-9._:-]{8,100}")
                    String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        IdempotentResult<OrderResponse> result = idempotencyService.execute(
                "orders.place:" + user.subject(),
                idempotencyKey,
                request,
                HttpStatus.CREATED.value(),
                OrderResponse.class,
                () -> OrderResponse.from(orderService.placeOrder(user, request.toCommand())));

        OrderResponse order = result.response();
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id()))
                .header(IDEMPOTENT_REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(order);
    }

    @GetMapping
    @Operation(summary = "List my orders", description = "Newest first.")
    public PageResponse<OrderSummaryResponse> listMyOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal Jwt jwt) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "placedAt", "id"));
        return PageResponse.from(
                orderService.listCustomerOrders(CurrentUser.from(jwt), status, pageRequest),
                OrderSummaryResponse::from);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get one of my orders")
    public OrderResponse getMyOrder(@PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
        return OrderResponse.from(orderService.getCustomerOrder(CurrentUser.from(jwt), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel one of my orders", description = "Only possible while the order is not paid yet.")
    public OrderResponse cancelMyOrder(@PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
        return OrderResponse.from(orderService.cancelCustomerOrder(CurrentUser.from(jwt), orderId));
    }
}
