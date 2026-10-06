package com.kestrel.commerce.customer.domain;

import com.kestrel.commerce.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * A shopper, linked to an identity provider account through {@code subject}.
 *
 * <p>Credentials and profile data are owned by the identity provider. We keep a local copy of the profile so orders can
 * reference a customer and back-office screens can display a name; it is refreshed from the token on each login.
 */
@Entity
@Table(name = "customers")
public class Customer extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "subject", nullable = false, updatable = false)
    private String subject;

    @Column(name = "email")
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    protected Customer() {
        // for JPA
    }

    /** @return {@code true} if the profile changed */
    public boolean syncProfile(String email, String firstName, String lastName) {
        boolean changed = !Objects.equals(this.email, email)
                || !Objects.equals(this.firstName, firstName)
                || !Objects.equals(this.lastName, lastName);
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        return changed;
    }

    public UUID getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Customer customer && id.equals(customer.getId()));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
