package com.kestrel.commerce.inventory.api;

import com.kestrel.commerce.inventory.domain.InventoryItem;
import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(UUID productId, int onHand, int reserved, int available, Instant updatedAt) {

    static InventoryResponse from(InventoryItem item) {
        return new InventoryResponse(
                item.getProductId(), item.getOnHand(), item.getReserved(), item.available(), item.getUpdatedAt());
    }
}
