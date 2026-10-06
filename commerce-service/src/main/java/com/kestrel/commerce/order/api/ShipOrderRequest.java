package com.kestrel.commerce.order.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShipOrderRequest(
        @Schema(example = "Colissimo") @NotBlank @Size(max = 50)
        String carrier,

        @Schema(example = "6A12345678901") @NotBlank @Size(max = 100)
        String trackingNumber) {}
