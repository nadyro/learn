package com.kestrel.commerce.catalog.api;

import com.kestrel.commerce.catalog.application.UpdateProductCommand;
import com.kestrel.commerce.shared.web.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full replacement of the editable fields of a product (PUT semantics). The SKU cannot be changed. */
public record UpdateProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @NotNull @Valid MoneyDto price) {

    UpdateProductCommand toCommand() {
        return new UpdateProductCommand(name.strip(), description, price.toMoney());
    }
}
