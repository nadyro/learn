package com.kestrel.commerce;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kestrel.commerce.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Checks the OpenAPI document and writes it to {@code target/openapi.json}. CI publishes this file as a build artifact
 * so API changes can be reviewed (and diffed) in pull requests.
 */
class OpenApiIT extends IntegrationTest {

    @Test
    void the_openapi_document_describes_the_public_api() throws Exception {
        String document = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Kestrel Outfitters - Commerce API"))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products'].get").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer-jwt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Files.writeString(Path.of("target", "openapi.json"), document);
    }
}
