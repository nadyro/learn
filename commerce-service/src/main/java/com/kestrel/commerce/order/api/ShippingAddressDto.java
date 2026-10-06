package com.kestrel.commerce.order.api;

import com.kestrel.commerce.order.domain.ShippingAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShippingAddressDto(
        @Schema(example = "Alice Martin") @NotBlank @Size(max = 100)
        String recipientName,

        @Schema(example = "12 Rue des Alpes") @NotBlank @Size(max = 200)
        String line1,

        @Size(max = 200) String line2,

        @Schema(example = "Grenoble") @NotBlank @Size(max = 100)
        String city,

        @Schema(example = "38000") @NotBlank @Size(max = 20) String postalCode,

        @Schema(example = "FR", description = "ISO 3166-1 alpha-2 country code") @NotBlank @Pattern(regexp = "[A-Z]{2}")
        String countryCode) {

    static ShippingAddressDto from(ShippingAddress address) {
        return new ShippingAddressDto(
                address.recipientName(),
                address.line1(),
                address.line2(),
                address.city(),
                address.postalCode(),
                address.countryCode());
    }

    ShippingAddress toDomain() {
        return new ShippingAddress(
                recipientName.strip(), line1.strip(), line2, city.strip(), postalCode.strip(), countryCode);
    }
}
