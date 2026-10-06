package com.kestrel.commerce.order.api;

import com.kestrel.commerce.order.application.PlaceOrderCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(
        @NotEmpty @Size(max = 50) List<@Valid @NotNull Item> items,
        @NotNull @Valid ShippingAddressDto shippingAddress) {

    public record Item(
            @NotNull UUID productId,

            @Schema(example = "1") @NotNull @Min(1) @Max(100)
            Integer quantity) {}

    PlaceOrderCommand toCommand() {
        return new PlaceOrderCommand(
                items.stream()
                        .map(item -> new PlaceOrderCommand.Item(item.productId(), item.quantity()))
                        .toList(),
                shippingAddress.toDomain());
    }
}
