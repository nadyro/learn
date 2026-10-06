package com.kestrel.commerce.catalog.application;

import com.kestrel.commerce.shared.domain.Money;

public record CreateProductCommand(String sku, String name, String description, Money price, int initialStock) {}
