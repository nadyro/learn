package com.kestrel.commerce.shared.security;

import com.kestrel.commerce.shared.web.CorsProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP security rules.
 *
 * <p>The service is a stateless OAuth2 resource server: every request carries a JWT access token issued by our
 * identity provider (Keycloak locally). There are no sessions and no cookies, which is why CSRF protection is disabled.
 *
 * <p>Authorization is deny-by-default: any route not listed below is rejected. When you add an endpoint, add its rule
 * here and a test in {@code SecurityIT}.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
        AuthenticationEntryPoint entryPoint = ProblemDetailsSecurityHandlers.authenticationEntryPoint(jsonMapper);
        AccessDeniedHandler accessDeniedHandler = ProblemDetailsSecurityHandlers.accessDeniedHandler(jsonMapper);

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Operational endpoints. Actuator listens on a separate, cluster-internal port.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/actuator/prometheus")
                        .permitAll()
                        // API documentation
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        // Public catalog browsing
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/*")
                        .permitAll()
                        // Authenticated by HMAC signature instead of a JWT, see WebhookSignatureVerifier
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/payments")
                        .permitAll()
                        // Back-office
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")
                        // Everything else in the API requires a customer account
                        .requestMatchers("/api/v1/**")
                        .hasRole("CUSTOMER")
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                                jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("Location", "X-Request-Id", "Idempotent-Replayed"));
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
