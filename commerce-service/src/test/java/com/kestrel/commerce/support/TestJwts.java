package com.kestrel.commerce.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Authenticates MockMvc requests without an identity provider. The JWT is not signed nor validated: Spring Security
 * puts it directly in the security context, as if it had been decoded from a real token.
 */
public final class TestJwts {

    private TestJwts() {}

    /** A customer with a random, unique subject. */
    public static JwtRequestPostProcessor newCustomer() {
        return customer("customer-" + UUID.randomUUID());
    }

    public static JwtRequestPostProcessor customer(String subject) {
        return jwt().jwt(token -> token.subject(subject)
                        .claim("preferred_username", subject)
                        .claim("email", subject + "@example.com")
                        .claim("given_name", "Test")
                        .claim("family_name", "Customer"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    public static JwtRequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin-" + UUID.randomUUID()).claim("preferred_username", "olivia"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
