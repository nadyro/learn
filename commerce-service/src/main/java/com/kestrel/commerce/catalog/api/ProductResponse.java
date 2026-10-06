package com.kestrel.commerce.catalog.api;

import com.kestrel.commerce.catalog.domain.Product;
import com.kestrel.commerce.catalog.domain.ProductStatus;
import com.kestrel.commerce.shared.web.MoneyDto;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        MoneyDto price,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt) {

    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                MoneyDto.from(product.getPrice()),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
