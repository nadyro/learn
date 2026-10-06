package com.kestrel.commerce.catalog.api;

import com.kestrel.commerce.catalog.application.CreateProductCommand;
import com.kestrel.commerce.shared.web.MoneyDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @Schema(example = "TENT-ALPINE-2P", description = "Unique stock keeping unit, stored upper-case")
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9-]{2,63}")
        String sku,

        @Schema(example = "Alpine 2-person tent") @NotBlank @Size(max = 200)
        String name,

        @Size(max = 4000) String description,
        @NotNull @Valid MoneyDto price,

        @Schema(description = "Units in the warehouse when the product is created. Defaults to 0.", example = "40")
        @Min(0)
        @Max(100_000)
        Integer initialStock) {

    CreateProductCommand toCommand() {
        return new CreateProductCommand(
                sku, name.strip(), description, price.toMoney(), initialStock == null ? 0 : initialStock);
    }
}
