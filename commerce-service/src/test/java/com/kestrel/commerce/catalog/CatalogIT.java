package com.kestrel.commerce.catalog;

import static com.kestrel.commerce.support.TestJwts.admin;
import static com.kestrel.commerce.support.TestJwts.newCustomer;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kestrel.commerce.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CatalogIT extends IntegrationTest {

    @Test
    void a_new_product_is_visible_in_the_shop_with_its_initial_stock() throws Exception {
        UUID productId = createProduct("129.90", 12);

        mvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.amount").value(129.90))
                .andExpect(jsonPath("$.price.currency").value("EUR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        getInventory(productId).andExpect(jsonPath("$.onHand").value(12));
    }

    @Test
    void skus_are_unique_regardless_of_case() throws Exception {
        String sku = "IT-DUP-" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"sku":"%s","name":"Original","price":{"amount":10.00,"currency":"EUR"},"initialStock":0}
                """;
        mvc.perform(post("/api/v1/admin/products")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(sku.toUpperCase())))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/admin/products")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(sku.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_ALREADY_EXISTS"));
    }

    @Test
    void archived_products_disappear_from_the_shop_but_stay_in_the_back_office() throws Exception {
        UUID productId = createProduct("10.00", 1);

        mvc.perform(post("/api/v1/admin/products/{id}/archive", productId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mvc.perform(get("/api/v1/products").param("size", "100"))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(productId.toString()))));
        mvc.perform(get("/api/v1/admin/products/{id}", productId).with(admin())).andExpect(status().isOk());
    }

    @Test
    void archived_products_cannot_be_ordered() throws Exception {
        UUID productId = createProduct("10.00", 5);
        mvc.perform(post("/api/v1/admin/products/{id}/archive", productId).with(admin()));

        placeOrder(newCustomer(), newIdempotencyKey(), orderRequest(productId, 1))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_PURCHASABLE"))
                .andExpect(jsonPath("$.productIds[0]").value(productId.toString()));
    }

    @Test
    void the_shop_can_search_products_by_name() throws Exception {
        String marker = "Zephyr" + UUID.randomUUID().toString().substring(0, 6);
        String body = """
                {"sku":"IT-%s","name":"%s ultralight quilt","price":{"amount":10.00,"currency":"EUR"},"initialStock":0}
                """.formatted(marker.toUpperCase(), marker);
        mvc.perform(post("/api/v1/admin/products")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/products").param("q", marker.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value(marker + " ultralight quilt"));
    }

    @Test
    void updating_a_product_changes_its_price_for_new_orders_only() throws Exception {
        UUID productId = createProduct("10.00", 5);
        UUID orderId = placeOrder(newCustomer(), productId, 1);

        mvc.perform(put("/api/v1/admin/products/{id}", productId)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"price\":{\"amount\":15.50,\"currency\":\"EUR\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.price.amount").value(15.50));

        mvc.perform(get("/api/v1/admin/orders/{id}", orderId).with(admin()))
                .andExpect(jsonPath("$.lines[0].unitPrice.amount").value(10.00))
                .andExpect(jsonPath("$.lines[0].productName").value(startsWith("Test product")));
    }

    @Test
    void invalid_products_are_rejected_with_the_list_of_invalid_fields() throws Exception {
        mvc.perform(post("/api/v1/admin/products")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"!\",\"name\":\"\",\"price\":{\"amount\":-1,\"currency\":\"euro\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("name", "price.amount", "price.currency", "sku")));
    }
}
