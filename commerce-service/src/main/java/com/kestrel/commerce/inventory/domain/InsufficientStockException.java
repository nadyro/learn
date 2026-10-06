package com.kestrel.commerce.inventory.domain;

import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Thrown when an order asks for more units than available. Lists every product that is short, not just the first. */
public class InsufficientStockException extends DomainException {

    public record Shortage(UUID productId, int requested, int available) {}

    public InsufficientStockException(List<Shortage> shortages) {
        super(
                ErrorCode.INSUFFICIENT_STOCK,
                "Not enough stock for " + shortages.size() + " product(s), see 'shortages'.",
                Map.of("shortages", List.copyOf(shortages)));
    }
}
