package com.kestrel.commerce.order.domain;

import com.kestrel.commerce.shared.domain.Money;
import java.util.UUID;

/** Data needed to add a line to a new order: a snapshot of the product at the time of purchase. */
public record NewOrderLine(UUID productId, String sku, String productName, Money unitPrice, int quantity) {}
