package com.kestrel.commerce.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document served at {@code /v3/api-docs}, with Swagger UI at {@code /swagger-ui.html}. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    OpenAPI commerceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Kestrel Outfitters - Commerce API")
                        .description("Catalog, inventory, customers and orders. Owned by the Commerce team.")
                        .version("v1")
                        .contact(new Contact().name("Commerce team").email("commerce-team@kestrel-outfitters.example")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Access token issued by the Kestrel identity provider")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
