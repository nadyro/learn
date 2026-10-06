package com.kestrel.commerce.customer.api;

import com.kestrel.commerce.customer.domain.Customer;
import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(UUID id, String email, String firstName, String lastName, Instant createdAt) {

    static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getEmail(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getCreatedAt());
    }
}
