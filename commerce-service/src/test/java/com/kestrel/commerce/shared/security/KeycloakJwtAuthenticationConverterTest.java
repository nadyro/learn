package com.kestrel.commerce.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    void realm_roles_become_spring_security_roles() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("customer", "admin", "offline_access"))));

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication.getName()).isEqualTo("user-123");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_ADMIN", "ROLE_OFFLINE_ACCESS");
    }

    @Test
    void a_token_without_realm_roles_has_no_authorities() {
        assertThat(converter.convert(jwt(Map.of("scope", "profile"))).getAuthorities())
                .isEmpty();
        assertThat(converter
                        .convert(jwt(Map.of("realm_access", Map.of("roles", "not-a-list"))))
                        .getAuthorities())
                .isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> c.putAll(claims))
                .build();
    }
}
