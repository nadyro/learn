package com.kestrel.commerce.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
        "PENDING_PAYMENT, PAID,            true",
        "PENDING_PAYMENT, CANCELLED,       true",
        "PENDING_PAYMENT, SHIPPED,         false",
        "PENDING_PAYMENT, DELIVERED,       false",
        "PAID,            SHIPPED,         true",
        "PAID,            CANCELLED,       false",
        "PAID,            PENDING_PAYMENT, false",
        "SHIPPED,         DELIVERED,       true",
        "SHIPPED,         CANCELLED,       false",
        "DELIVERED,       CANCELLED,       false",
        "CANCELLED,       PAID,            false",
        "CANCELLED,       PENDING_PAYMENT, false"
    })
    void transitions_follow_the_order_lifecycle(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
