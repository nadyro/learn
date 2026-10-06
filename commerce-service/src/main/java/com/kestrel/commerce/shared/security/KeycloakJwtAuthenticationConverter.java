package com.kestrel.commerce.shared.security;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps Keycloak realm roles ({@code realm_access.roles} claim) to Spring Security authorities.
 *
 * <p>A token containing {@code "realm_access": {"roles": ["customer", "admin"]}} results in the authorities
 * {@code ROLE_CUSTOMER} and {@code ROLE_ADMIN}, which is what {@code hasRole("ADMIN")} checks.
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, extractAuthorities(jwt), jwt.getSubject());
    }

    static Set<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null || !(realmAccess.get(ROLES) instanceof Collection<?> roles)) {
            return Set.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(role -> ROLE_PREFIX + ((String) role).toUpperCase(Locale.ROOT))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }
}
