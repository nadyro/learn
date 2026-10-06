package com.kestrel.commerce.shared.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origins (e.g. {@code https://shop.kestrel-outfitters.example}) allowed to call the API from a browser.
 *
 * <p>Empty by default: browsers are blocked unless an environment explicitly allows its front-end.
 */
@ConfigurationProperties(prefix = "commerce.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
