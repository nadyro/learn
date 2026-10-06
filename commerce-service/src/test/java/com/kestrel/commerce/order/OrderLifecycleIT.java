package com.kestrel.commerce.order;

import static com.kestrel.commerce.support.TestJwts.admin;
import static com.kestrel.commerce.support.TestJwts.newCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kestrel.commerce.support.IntegrationTest;
import com.kestrel.commerce.support.OrderEventsReader;
import com.kestrel.commerce.support.WebhookSigner;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;

/** The happy path, end to end: place, pay, ship and deliver an order, and check the events other teams receive. */
class OrderLifecycleIT extends IntegrationTest {

    @Autowired
    KafkaContainer kafka;

    @Test
    void an_order_goes_from_placement_to_delivery_and_publishes_one_event_per_step() throws Exception {
        UUID productId = createProduct("59.90", 10);
        JwtRequestPostProcessor customer = newCustomer();
        String idempotencyKey = newIdempotencyKey();
        String request = orderRequest(productId, 2);

        // 1. Place the order: stock is reserved, the order waits for payment
        String placed = placeOrder(customer, idempotencyKey, request)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/orders/")))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.orderNumber").value(startsWith("KO-")))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.total.amount").value(119.80))
                .andExpect(jsonPath("$.total.currency").value("EUR"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID orderId = UUID.fromString(JsonPath.read(placed, "$.id"));
        getInventory(productId)
                .andExpect(jsonPath("$.onHand").value(10))
                .andExpect(jsonPath("$.reserved").value(2))
                .andExpect(jsonPath("$.available").value(8));

        // 2. The client did not get the response and retries: same order, nothing reserved twice
        placeOrder(customer, idempotencyKey, request)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(orderId.toString()));
        getInventory(productId).andExpect(jsonPath("$.reserved").value(2));

        // 3. The payment provider confirms the payment (twice, as providers deliver at least once)
        String payment = WebhookSigner.paymentSucceeded("evt_" + UUID.randomUUID(), orderId, "119.80", "EUR");
        sendPaymentWebhook(payment)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PROCESSED"));
        sendPaymentWebhook(payment)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));
        mvc.perform(get("/api/v1/orders/{id}", orderId).with(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());

        // 4. The warehouse ships it: the units leave the stock
        mvc.perform(post("/api/v1/admin/orders/{id}/shipment", orderId)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"Colissimo\",\"trackingNumber\":\"6A12345678901\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.trackingNumber").value("6A12345678901"));
        getInventory(productId)
                .andExpect(jsonPath("$.onHand").value(8))
                .andExpect(jsonPath("$.reserved").value(0));

        // 5. The carrier delivers it
        mvc.perform(post("/api/v1/admin/orders/{id}/delivery", orderId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // 6. Downstream consumers received every step, in order, keyed by order ID
        try (OrderEventsReader events = new OrderEventsReader(kafka.getBootstrapServers(), jsonMapper)) {
            await().atMost(Duration.ofSeconds(20))
                    .untilAsserted(() -> assertThat(events.eventsFor(orderId))
                            .extracting(event -> event.get("eventType").asString())
                            .containsExactly("order.placed", "order.paid", "order.shipped", "order.delivered"));

            List<JsonNode> published = events.eventsFor(orderId);
            JsonNode orderPlaced = published.getFirst();
            assertThat(orderPlaced.get("eventId").asString()).isNotBlank();
            assertThat(orderPlaced.get("producer").asString()).isEqualTo("commerce-service");
            assertThat(orderPlaced.get("data").get("totalAmount").decimalValue())
                    .isEqualByComparingTo("119.80");
            assertThat(orderPlaced
                            .get("data")
                            .get("lines")
                            .get(0)
                            .get("productId")
                            .asString())
                    .isEqualTo(productId.toString());
        }
    }

    @Test
    void a_payment_for_the_wrong_amount_is_rejected_and_the_order_stays_unpaid() throws Exception {
        UUID productId = createProduct("10.00", 5);
        JwtRequestPostProcessor customer = newCustomer();
        UUID orderId = placeOrder(customer, productId, 1);

        sendPaymentWebhook(WebhookSigner.paymentSucceeded("evt_" + UUID.randomUUID(), orderId, "1.00", "EUR"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));

        mvc.perform(get("/api/v1/orders/{id}", orderId).with(customer))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void a_webhook_with_an_invalid_signature_is_rejected() throws Exception {
        String payload = WebhookSigner.paymentSucceeded("evt_forged", UUID.randomUUID(), "1.00", "EUR");

        mvc.perform(post("/api/v1/webhooks/payments")
                        .header("Payment-Signature", "t=" + (System.currentTimeMillis() / 1000) + ",v1=00ff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
    }

    @Test
    void reusing_an_idempotency_key_for_a_different_order_is_rejected() throws Exception {
        UUID productId = createProduct("10.00", 5);
        JwtRequestPostProcessor customer = newCustomer();
        String key = newIdempotencyKey();
        placeOrder(customer, key, orderRequest(productId, 1)).andExpect(status().isCreated());

        placeOrder(customer, key, orderRequest(productId, 2))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void the_same_idempotency_key_used_by_two_customers_creates_two_orders() throws Exception {
        UUID productId = createProduct("10.00", 5);
        String key = newIdempotencyKey();

        String first = placeOrder(newCustomer(), key, orderRequest(productId, 1))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String second = placeOrder(newCustomer(), key, orderRequest(productId, 1))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(JsonPath.<String>read(first, "$.id")).isNotEqualTo(JsonPath.read(second, "$.id"));
    }
}
