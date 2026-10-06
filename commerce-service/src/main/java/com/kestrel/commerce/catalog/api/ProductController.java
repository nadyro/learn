package com.kestrel.commerce.catalog.api;

import com.kestrel.commerce.catalog.application.CatalogService;
import com.kestrel.commerce.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, anonymous catalog browsing for the storefront. */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Public product catalog")
@SecurityRequirements // no authentication required
public class ProductController {

    private final CatalogService catalogService;

    public ProductController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @Operation(summary = "Search active products", description = "Sorted by name. Matches the name, case-insensitive.")
    public PageResponse<ProductResponse> searchProducts(
            @Parameter(description = "Text contained in the product name")
                    @RequestParam(required = false)
                    @Size(max = 100)
                    String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("name").and(Sort.by("id")));
        return PageResponse.from(catalogService.searchActiveProducts(q, pageRequest), ProductResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an active product")
    public ProductResponse getProduct(@PathVariable UUID id) {
        return ProductResponse.from(catalogService.getActiveProduct(id));
    }
}
