package com.kestrel.commerce.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void amounts_are_normalised_to_two_decimals_so_equals_is_safe() {
        assertThat(Money.of("10", "EUR")).isEqualTo(Money.of("10.00", "EUR"));
        assertThat(Money.of("10.5", "EUR").amount())
                .isEqualByComparingTo("10.50")
                .hasToString("10.50");
    }

    @Test
    void money_can_be_added_and_multiplied() {
        Money unitPrice = Money.of("19.99", "EUR");

        assertThat(unitPrice.multiply(3)).isEqualTo(Money.of("59.97", "EUR"));
        assertThat(unitPrice.add(Money.of("0.01", "EUR"))).isEqualTo(Money.of("20.00", "EUR"));
        assertThat(Money.zero("EUR").multiply(5)).isEqualTo(Money.zero("EUR"));
    }

    @Test
    void amounts_in_different_currencies_cannot_be_added() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of("1.00", "EUR").add(Money.of("1.00", "USD")))
                .withMessageContaining("EUR")
                .withMessageContaining("USD");
    }

    @Test
    void negative_amounts_are_rejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("-0.01", "EUR"));
    }

    @Test
    void amounts_with_more_than_two_decimals_are_rejected_instead_of_silently_rounded() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("1.005", "EUR"));
        assertThat(Money.of("1.500", "EUR").amount()).isEqualByComparingTo(new BigDecimal("1.5"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"eur", "EURO", "E1R", ""})
    void currency_must_be_an_iso_code(String currency) {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("1.00", currency));
    }

    @Test
    void to_string_is_readable_in_logs() {
        assertThat(Money.of("1234.5", "EUR")).hasToString("1234.50 EUR");
    }
}
