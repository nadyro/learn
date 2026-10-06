package com.kestrel.commerce.inventory.application;

import java.util.UUID;

/** A quantity of a product to reserve, release or ship. */
public record StockLine(UUID productId, int quantity) {

    public StockLine {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, got: " + quantity);
        }
    }
}
