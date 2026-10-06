package com.kestrel.commerce.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/** Where the order is delivered. A copy is kept on the order: later changes to the customer's address don't apply. */
@Embeddable
public record ShippingAddress(
        @Column(name = "ship_recipient_name", nullable = false)
        String recipientName,

        @Column(name = "ship_line1", nullable = false) String line1,
        @Column(name = "ship_line2") String line2,
        @Column(name = "ship_city", nullable = false) String city,
        @Column(name = "ship_postal_code", nullable = false) String postalCode,

        @Column(name = "ship_country_code", nullable = false)
        String countryCode) {

    public ShippingAddress {
        Objects.requireNonNull(recipientName, "recipientName must not be null");
        Objects.requireNonNull(line1, "line1 must not be null");
        Objects.requireNonNull(city, "city must not be null");
        Objects.requireNonNull(postalCode, "postalCode must not be null");
        Objects.requireNonNull(countryCode, "countryCode must not be null");
    }
}
