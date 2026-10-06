package com.kestrel.commerce.inventory.api;

import com.kestrel.commerce.inventory.application.InventoryService;
import com.kestrel.commerce.inventory.domain.InventoryItem;
import com.kestrel.commerce.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@Tag(name = "Inventory (admin)", description = "Stock levels and manual stock adjustments")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get the stock of a product")
    public InventoryResponse getInventory(@PathVariable UUID productId) {
        return InventoryResponse.from(inventoryService.getInventory(productId));
    }

    @PostMapping("/{productId}/adjustments")
    @Operation(summary = "Adjust the stock of a product", description = "Every adjustment is recorded for audit.")
    public InventoryResponse adjustStock(
            @PathVariable UUID productId,
            @Valid @RequestBody StockAdjustmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        InventoryItem item = inventoryService.adjustStock(
                productId,
                request.delta(),
                request.reason(),
                request.note(),
                CurrentUser.from(jwt).auditName());
        return InventoryResponse.from(item);
    }
}
