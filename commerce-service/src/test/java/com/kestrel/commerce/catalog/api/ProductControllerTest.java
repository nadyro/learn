package com.kestrel.commerce.catalog.api;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kestrel.commerce.catalog.application.CatalogService;
import com.kestrel.commerce.catalog.domain.Product;
import com.kestrel.commerce.shared.domain.Money;
import com.kestrel.commerce.shared.error.ErrorCode;
import com.kestrel.commerce.shared.error.NotFoundException;
import com.kestrel.commerce.shared.security.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web layer slice test: only the controller, the security rules and the error handling are loaded. The service is
 * mocked, so these tests are fast and focus on HTTP concerns (status codes, JSON shape, validation).
 */
@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CatalogService catalogService;

    @MockitoBean
    JwtDecoder jwtDecoder; // required by the resource server configuration, never called here

    @Test
    void anyone_can_browse_the_catalog_without_logging_in() throws Exception {
        Product tent = new Product("TENT-1", "Alpine tent", "4 seasons", Money.of("349.00", "EUR"));
        when(catalogService.searchActiveProducts(eq("tent"), any()))
                .thenReturn(new PageImpl<>(List.of(tent), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/products").param("q", "tent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("TENT-1"))
                .andExpect(jsonPath("$.content[0].price.amount").value(349.00))
                .andExpect(jsonPath("$.content[0].price.currency").value("EUR"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void errors_use_the_problem_details_format() throws Exception {
        UUID id = UUID.randomUUID();
        when(catalogService.getActiveProduct(id))
                .thenThrow(new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Product", id));

        mvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.type").value("https://api.kestrel-outfitters.example/problems/product-not-found"))
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Product " + id + " was not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/products/" + id))
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void the_page_size_is_capped_to_protect_the_database() throws Exception {
        mvc.perform(get("/api/v1/products").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void a_malformed_product_id_is_a_client_error_not_a_server_error() throws Exception {
        mvc.perform(get("/api/v1/products/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unexpected_errors_do_not_leak_internal_details() throws Exception {
        UUID id = UUID.randomUUID();
        when(catalogService.getActiveProduct(id)).thenThrow(new IllegalStateException("connection to db-7 refused"));

        mvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail", startsWith("An unexpected error occurred")));
    }
}
