package com.kestrel.commerce.catalog.domain;

import com.kestrel.commerce.shared.domain.AuditableEntity;
import com.kestrel.commerce.shared.domain.Ids;
import com.kestrel.commerce.shared.domain.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A product sold in the shop.
 *
 * <p>Entities in this codebase protect their own invariants: there are no public setters, state only changes through
 * methods named after business operations ({@link #updateDetails}, {@link #archive}).
 */
@Entity
@Table(name = "products")
public class Product extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "sku", nullable = false, updatable = false)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price_amount", nullable = false))
    @AttributeOverride(name = "currency", column = @Column(name = "price_currency", nullable = false))
    private Money price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    protected Product() {
        // for JPA
    }

    public Product(String sku, String name, String description, Money price) {
        this.id = Ids.newId();
        this.sku = normalizeSku(sku);
        this.status = ProductStatus.ACTIVE;
        updateDetails(name, description, price);
    }

    public static String normalizeSku(String sku) {
        return Objects.requireNonNull(sku, "sku must not be null").trim().toUpperCase(Locale.ROOT);
    }

    public void updateDetails(String name, String description, Money price) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.price = Objects.requireNonNull(price, "price must not be null");
    }

    public void archive() {
        this.status = ProductStatus.ARCHIVED;
    }

    public boolean isPurchasable() {
        return status == ProductStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Money getPrice() {
        return price;
    }

    public ProductStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Product product && id.equals(product.getId()));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
