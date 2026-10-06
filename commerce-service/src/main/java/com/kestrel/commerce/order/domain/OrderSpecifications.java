package com.kestrel.commerce.order.domain;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Reusable query criteria for dynamic order searches (all filters are optional and combined with AND). */
public final class OrderSpecifications {

    private OrderSpecifications() {}

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Order> belongsToCustomer(UUID customerId) {
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }
}
