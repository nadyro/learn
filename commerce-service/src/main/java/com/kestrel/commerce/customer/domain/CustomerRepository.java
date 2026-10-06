package com.kestrel.commerce.customer.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findBySubject(String subject);

    /**
     * Creates the customer unless one already exists for this subject.
     *
     * <p>A user's first requests can arrive in parallel (the storefront loads several pages at once). A plain "find,
     * then insert if missing" would let two requests insert the same customer; the second would fail on the unique
     * constraint. {@code ON CONFLICT DO NOTHING} makes the registration race-free.
     *
     * @return 1 if the row was inserted, 0 if it already existed
     */
    @Modifying
    @Query(value = """
            insert into customers (id, subject, email, first_name, last_name, created_at, updated_at, version)
            values (:id, :subject, :email, :firstName, :lastName, :now, :now, 0)
            on conflict (subject) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(UUID id, String subject, String email, String firstName, String lastName, Instant now);
}
