package com.kestrel.commerce.catalog.application;

import com.kestrel.commerce.catalog.domain.Product;
import com.kestrel.commerce.catalog.domain.ProductRepository;
import com.kestrel.commerce.catalog.domain.ProductStatus;
import com.kestrel.commerce.inventory.application.InventoryService;
import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import com.kestrel.commerce.shared.error.NotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Public API of the catalog module. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final ProductRepository products;
    private final InventoryService inventoryService;

    public CatalogService(ProductRepository products, InventoryService inventoryService) {
        this.products = products;
        this.inventoryService = inventoryService;
    }

    public Page<Product> searchActiveProducts(String query, Pageable pageable) {
        if (!StringUtils.hasText(query)) {
            return products.findByStatus(ProductStatus.ACTIVE, pageable);
        }
        return products.findByStatusAndNameContainingIgnoreCase(ProductStatus.ACTIVE, query.trim(), pageable);
    }

    /** Products as seen by shoppers: archived products do not exist for them. */
    public Product getActiveProduct(UUID id) {
        return products.findByIdAndStatus(id, ProductStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Product", id));
    }

    public Product getProduct(UUID id) {
        return products.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Product", id));
    }

    public Page<Product> listProducts(ProductStatus status, Pageable pageable) {
        return status == null ? products.findAll(pageable) : products.findByStatus(status, pageable);
    }

    /**
     * Returns the requested products, keyed by ID, after checking they can all be sold.
     *
     * @throws DomainException with {@link ErrorCode#PRODUCT_NOT_PURCHASABLE} if one is unknown or archived
     */
    public Map<UUID, Product> getPurchasableProducts(Collection<UUID> ids) {
        Map<UUID, Product> found = products.findAllByIdIn(ids).stream()
                .filter(Product::isPurchasable)
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Set<UUID> unavailable =
                ids.stream().filter(id -> !found.containsKey(id)).collect(Collectors.toSet());
        if (!unavailable.isEmpty()) {
            throw new DomainException(
                    ErrorCode.PRODUCT_NOT_PURCHASABLE,
                    "Some products do not exist or are no longer sold.",
                    Map.of("productIds", List.copyOf(unavailable)));
        }
        return found;
    }

    @Transactional
    public Product createProduct(CreateProductCommand command, String performedBy) {
        String sku = Product.normalizeSku(command.sku());
        if (products.existsBySku(sku)) {
            throw new DomainException(
                    ErrorCode.SKU_ALREADY_EXISTS, "A product with SKU " + sku + " already exists.", Map.of("sku", sku));
        }
        Product product = products.save(new Product(sku, command.name(), command.description(), command.price()));
        inventoryService.registerProduct(product.getId(), command.initialStock(), performedBy);
        log.info("Product {} ({}) created by {}", product.getId(), sku, performedBy);
        return product;
    }

    @Transactional
    public Product updateProduct(UUID id, UpdateProductCommand command) {
        Product product = getProduct(id);
        product.updateDetails(command.name(), command.description(), command.price());
        return product;
    }

    @Transactional
    public Product archiveProduct(UUID id) {
        Product product = getProduct(id);
        product.archive();
        log.info("Product {} archived", id);
        return product;
    }
}
