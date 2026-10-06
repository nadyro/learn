package com.kestrel.commerce.inventory.api;

import com.kestrel.commerce.inventory.domain.AdjustmentReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(
        @Schema(description = "Units to add (positive) or remove (negative)", example = "25")
        @NotNull
        @Min(-100_000)
        @Max(100_000)
        Integer delta,

        @NotNull AdjustmentReason reason,

        @Schema(example = "Delivery note DN-2026-0042") @Size(max = 500)
        String note) {}
