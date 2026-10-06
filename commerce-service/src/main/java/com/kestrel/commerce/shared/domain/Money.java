package com.kestrel.commerce.shared.domain;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An amount of money in a given ISO-4217 currency.
 *
 * <p>Rules of thumb that apply everywhere in this codebase:
 *
 * <ul>
 *   <li>Never use {@code double} or {@code float} for money, always {@link BigDecimal}.
 *   <li>Never compare {@link BigDecimal}s with {@code equals} ({@code 1.0 != 1.00}); this record normalises the scale
 *       to 2 so that {@code equals} is safe on {@code Money} itself.
 *   <li>Never add amounts in different currencies.
 * </ul>
 */
@Embeddable
public record Money(BigDecimal amount, String currency) {

    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");
    private static final int SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (!CURRENCY_CODE.matcher(currency).matches()) {
            throw new IllegalArgumentException("currency must be an ISO-4217 code, got: " + currency);
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative, got: " + amount);
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("amount must have at most " + SCALE + " decimals, got: " + amount);
        }
        amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative, got: " + quantity);
        }
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public boolean hasSameCurrencyAs(Money other) {
        return currency.equals(other.currency);
    }

    private void requireSameCurrency(Money other) {
        if (!hasSameCurrencyAs(other)) {
            throw new IllegalArgumentException("Cannot combine " + currency + " with " + other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
