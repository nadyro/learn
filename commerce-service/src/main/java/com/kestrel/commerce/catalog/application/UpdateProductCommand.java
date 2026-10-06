package com.kestrel.commerce.catalog.application;

import com.kestrel.commerce.shared.domain.Money;

public record UpdateProductCommand(String name, String description, Money price) {}
