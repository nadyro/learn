package com.kestrel.commerce.order;

import static com.kestrel.commerce.support.TestJwts.admin;
import static com.kestrel.commerce.support.TestJwts.newCustomer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kestrel.commerce.support.IntegrationTest;
import com.kestrel.commerce.support.WebhookSigner;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

class OrderCancellationIT extends IntegrationTest {

    @Test
    void cancelling_an_unpaid_order_releases_the_reserved_stock() throws Exception {
        UUID productId = createProduct("25.00", 4);
        JwtRequestPostProcessor customer = newCustomer();
        UUID orderId = placeOrder(customer, productId, 3);
        getInventory(productId).andExpect(jsonPath("$.available").value(1));

        mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).with(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("CUSTOMER_REQUEST"));

        getInventory(productId)
                .andExpect(jsonPath("$.reserved").value(0))
                .andExpect(jsonPath("$.available").value(4));
    }

    @Test
    void an_order_cannot_be_cancelled_twice() throws Exception {
        UUID productId = createProduct("25.00", 4);
        JwtRequestPostProcessor customer = newCustomer();
        UUID orderId = placeOrder(customer, productId, 1);
        mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).with(customer)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).with(customer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"))
                .andExpect(jsonPath("$.currentStatus").value("CANCELLED"));
    }

    @Test
    void a_shipped_order_cannot_be_cancelled_by_the_back_office() throws Exception {
        UUID productId = createProduct("25.00", 4);
        UUID orderId = placeOrder(newCustomer(), productId, 1);
        sendPaymentWebhook(WebhookSigner.paymentSucceeded("evt_" + UUID.randomUUID(), orderId, "25.00", "EUR"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/orders/{id}/shipment", orderId)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"DHL\",\"trackingNumber\":\"JD0001\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/admin/orders/{id}/cancel", orderId).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
    }

    @Test
    void customers_cannot_see_or_cancel_other_customers_orders() throws Exception {
        UUID productId = createProduct("25.00", 4);
        UUID orderId = placeOrder(newCustomer(), productId, 1);
        JwtRequestPostProcessor someoneElse = newCustomer();

        // 404 rather than 403: we do not even confirm that the order exists
        mvc.perform(get("/api/v1/orders/{id}", orderId).with(someoneElse))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).with(someoneElse))
                .andExpect(status().isNotFound());
    }

    @Test
    void customers_list_only_their_own_orders_newest_first() throws Exception {
        UUID productId = createProduct("5.00", 10);
        JwtRequestPostProcessor customer = newCustomer();
        UUID first = placeOrder(customer, productId, 1);
        UUID second = placeOrder(customer, productId, 1);
        placeOrder(newCustomer(), productId, 1);

        mvc.perform(get("/api/v1/orders").with(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(second.toString()))
                .andExpect(jsonPath("$.content[1].id").value(first.toString()));
    }
}
