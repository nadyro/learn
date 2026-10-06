package com.kestrel.commerce.customer.application;

import com.kestrel.commerce.customer.domain.Customer;
import com.kestrel.commerce.customer.domain.CustomerRepository;
import com.kestrel.commerce.shared.domain.Ids;
import com.kestrel.commerce.shared.security.CurrentUser;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the customer module. */
@Service
@Transactional
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customers;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    /**
     * Returns the customer for the authenticated user, creating it on first use ("just-in-time provisioning") and
     * keeping the profile in sync with the identity provider.
     */
    public Customer getOrRegister(CurrentUser user) {
        Customer customer = customers.findBySubject(user.subject()).orElseGet(() -> register(user));
        if (customer.syncProfile(user.email(), user.givenName(), user.familyName())) {
            log.debug("Profile of customer {} refreshed from identity provider", customer.getId());
        }
        return customer;
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findBySubject(String subject) {
        return customers.findBySubject(subject);
    }

    private Customer register(CurrentUser user) {
        int inserted = customers.insertIfAbsent(
                Ids.newId(), user.subject(), user.email(), user.givenName(), user.familyName(), clock.instant());
        if (inserted == 1) {
            log.info("Registered new customer for subject {}", user.subject());
        }
        return customers
                .findBySubject(user.subject())
                .orElseThrow(() -> new IllegalStateException("Customer missing right after insert"));
    }
}
