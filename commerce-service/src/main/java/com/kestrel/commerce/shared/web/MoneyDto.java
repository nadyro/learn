package com.kestrel.commerce.shared.web;

import com.kestrel.commerce.shared.domain.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

@Schema(description = "An amount of money")
public record MoneyDto(
        @Schema(example = "129.90") @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal amount,

        @Schema(example = "EUR", description = "ISO-4217 currency code") @NotBlank @Pattern(regexp = "[A-Z]{3}")
        String currency) {

    public static MoneyDto from(Money money) {
        return new MoneyDto(money.amount(), money.currency());
    }

    public Money toMoney() {
        return new Money(amount, currency);
    }
}
