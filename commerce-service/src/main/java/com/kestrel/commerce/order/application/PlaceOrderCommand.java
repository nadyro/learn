package com.kestrel.commerce.order.application;

import com.kestrel.commerce.order.domain.ShippingAddress;
import java.util.List;
import java.util.UUID;

public record PlaceOrderCommand(List<Item> items, ShippingAddress shippingAddress) {

    public record Item(UUID productId, int quantity) {}

    public PlaceOrderCommand {
        items = List.copyOf(items);
    }
}
