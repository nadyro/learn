package com.kestrel.commerce.shared.security;

import java.util.Objects;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated caller, extracted from the validated access token.
 *
 * <p>{@code subject} (the JWT {@code sub} claim) is the stable identifier of a user in our identity provider. Never use
 * the email or username as an identifier: both can change.
 */
public record CurrentUser(String subject, String username, String email, String givenName, String familyName) {

    public CurrentUser {
        Objects.requireNonNull(subject, "subject must not be null");
    }

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"));
    }

    /** Human-friendly identifier for audit logs. */
    public String auditName() {
        return username != null ? username : subject;
    }
}
