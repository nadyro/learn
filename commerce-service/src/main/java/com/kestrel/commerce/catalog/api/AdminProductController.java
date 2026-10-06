package com.kestrel.commerce.catalog.api;

import com.kestrel.commerce.catalog.application.CatalogService;
import com.kestrel.commerce.catalog.domain.Product;
import com.kestrel.commerce.catalog.domain.ProductStatus;
import com.kestrel.commerce.shared.security.CurrentUser;
import com.kestrel.commerce.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Catalog management for the back-office. Requires the {@code admin} role. */
@RestController
@RequestMapping("/api/v1/admin/products")
@Tag(name = "Products (admin)", description = "Catalog management")
public class AdminProductController {

    private final CatalogService catalogService;

    public AdminProductController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @Operation(summary = "List products, including archived ones", description = "Newest first.")
    public PageResponse<ProductResponse> listProducts(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.from(catalogService.listProducts(status, pageRequest), ProductResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product in any status")
    public ProductResponse getProduct(@PathVariable UUID id) {
        return ProductResponse.from(catalogService.getProduct(id));
    }

    @PostMapping
    @Operation(summary = "Create a product and its inventory record")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request, @AuthenticationPrincipal Jwt jwt) {
        Product product = catalogService.createProduct(
                request.toCommand(), CurrentUser.from(jwt).auditName());
        return ResponseEntity.created(URI.create("/api/v1/admin/products/" + product.getId()))
                .body(ProductResponse.from(product));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update the name, description and price of a product")
    public ProductResponse updateProduct(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return ProductResponse.from(catalogService.updateProduct(id, request.toCommand()));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a product", description = "Removes it from the shop. Existing orders are unaffected.")
    public ProductResponse archiveProduct(@PathVariable UUID id) {
        return ProductResponse.from(catalogService.archiveProduct(id));
    }
}
