package com.kestrel.commerce.order;

import static com.kestrel.commerce.support.TestJwts.admin;
import static com.kestrel.commerce.support.TestJwts.newCustomer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kestrel.commerce.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

class AdminOrderSearchIT extends IntegrationTest {

    @Test
    void the_back_office_can_filter_orders_by_customer_and_status() throws Exception {
        UUID productId = createProduct("12.00", 10);
        JwtRequestPostProcessor customer = newCustomer();
        UUID kept = placeOrder(customer, productId, 1);
        UUID cancelled = placeOrder(customer, productId, 1);
        mvc.perform(post("/api/v1/orders/{id}/cancel", cancelled).with(customer))
                .andExpect(status().isOk());
        String customerId = JsonPath.read(
                mvc.perform(get("/api/v1/customers/me").with(customer))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        mvc.perform(get("/api/v1/admin/orders").param("customerId", customerId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get("/api/v1/admin/orders")
                        .param("customerId", customerId)
                        .param("status", "PENDING_PAYMENT")
                        .with(admin()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(kept.toString()))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_PAYMENT"));
    }

    @Test
    void an_unknown_status_filter_is_a_client_error() throws Exception {
        mvc.perform(get("/api/v1/admin/orders").param("status", "LOST_IN_SPACE").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void payment_events_we_do_not_handle_are_acknowledged_and_ignored() throws Exception {
        String refund = """
                {"id":"evt_%s","type":"refund.created","createdAt":"2026-10-01T09:30:00Z","data":{}}
                """.formatted(UUID.randomUUID());

        sendPaymentWebhook(refund)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IGNORED"));
    }

    @Test
    void a_signed_but_malformed_payment_event_is_rejected() throws Exception {
        sendPaymentWebhook("{\"id\":\"evt_1\",\"type\":\"payment.succeeded\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        sendPaymentWebhook("not json").andExpect(status().isBadRequest());
    }
}
