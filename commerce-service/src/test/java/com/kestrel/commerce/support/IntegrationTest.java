package com.kestrel.commerce.support;

import static com.kestrel.commerce.support.TestJwts.admin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base class of integration tests: the whole application against real Postgres and Kafka containers.
 *
 * <p>All subclasses share one Spring context (and therefore one set of containers) because they use exactly the same
 * configuration. Do not add {@code @MockitoBean} or {@code @TestPropertySource} to a subclass: it would create a new
 * context and new containers, making the build much slower.
 *
 * <p>Tests share the database too, so they must not depend on its global state: create the data you need (with random
 * SKUs and subjects) and only assert on that data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper jsonMapper;

    protected UUID createProduct(String price, int initialStock) throws Exception {
        String sku = "IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String body = """
                {"sku":"%s","name":"Test product %s","description":"Created by a test",
                 "price":{"amount":%s,"currency":"EUR"},"initialStock":%d}
                """.formatted(sku, sku, price, initialStock);
        String response = mvc.perform(post("/api/v1/admin/products")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    protected static String orderRequest(UUID productId, int quantity) {
        return """
                {"items":[{"productId":"%s","quantity":%d}],
                 "shippingAddress":{"recipientName":"Test Customer","line1":"1 Test Street","city":"Grenoble",
                                    "postalCode":"38000","countryCode":"FR"}}
                """.formatted(productId, quantity);
    }

    protected ResultActions placeOrder(JwtRequestPostProcessor customer, String idempotencyKey, String body)
            throws Exception {
        return mvc.perform(post("/api/v1/orders")
                .with(customer)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    protected UUID placeOrder(JwtRequestPostProcessor customer, UUID productId, int quantity) throws Exception {
        String response = placeOrder(customer, newIdempotencyKey(), orderRequest(productId, quantity))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    protected ResultActions getInventory(UUID productId) throws Exception {
        return mvc.perform(get("/api/v1/admin/inventory/{productId}", productId).with(admin()));
    }

    protected ResultActions sendPaymentWebhook(String payload) throws Exception {
        return mvc.perform(post("/api/v1/webhooks/payments")
                .header("Payment-Signature", WebhookSigner.sign(payload))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    protected static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }
}
