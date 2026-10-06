package com.kestrel.commerce.order.domain;

import com.kestrel.commerce.shared.domain.Ids;
import com.kestrel.commerce.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One product in an order.
 *
 * <p>SKU, name and price are copied from the catalog when the order is placed. They must never be read from the
 * catalog afterwards: a price change tomorrow must not change what the customer paid today.
 */
@Entity
@Table(name = "order_lines")
public class OrderLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "sku", nullable = false, updatable = false)
    private String sku;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, updatable = false)
    private BigDecimal lineTotal;

    protected OrderLine() {
        // for JPA
    }

    OrderLine(Order order, int lineNumber, NewOrderLine line) {
        if (line.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.id = Ids.newId();
        this.order = order;
        this.lineNumber = lineNumber;
        this.productId = line.productId();
        this.sku = line.sku();
        this.productName = line.productName();
        this.unitPrice = line.unitPrice().amount();
        this.quantity = line.quantity();
        this.lineTotal = line.unitPrice().multiply(line.quantity()).amount();
    }

    public UUID getId() {
        return id;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public Money getUnitPrice() {
        return new Money(unitPrice, order.getCurrency());
    }

    public int getQuantity() {
        return quantity;
    }

    public Money getLineTotal() {
        return new Money(lineTotal, order.getCurrency());
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof OrderLine line && id.equals(line.getId()));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
