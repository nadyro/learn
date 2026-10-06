package com.kestrel.commerce.catalog.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndStatus(UUID id, ProductStatus status);

    boolean existsBySku(String sku);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    /** Spring Data escapes {@code %} and {@code _} in the search term, so user input cannot alter the LIKE pattern. */
    Page<Product> findByStatusAndNameContainingIgnoreCase(ProductStatus status, String name, Pageable pageable);

    List<Product> findAllByIdIn(Collection<UUID> ids);
}
