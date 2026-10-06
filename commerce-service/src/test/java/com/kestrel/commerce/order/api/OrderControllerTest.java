package com.kestrel.commerce.order.api;

import static com.kestrel.commerce.support.TestJwts.customer;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kestrel.commerce.order.application.OrderService;
import com.kestrel.commerce.order.domain.NewOrderLine;
import com.kestrel.commerce.order.domain.Order;
import com.kestrel.commerce.order.domain.ShippingAddress;
import com.kestrel.commerce.shared.domain.Money;
import com.kestrel.commerce.shared.idempotency.IdempotencyService;
import com.kestrel.commerce.shared.idempotency.IdempotentResult;
import com.kestrel.commerce.shared.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    private static final String VALID_ORDER = """
            {"items":[{"productId":"0192f0a0-0000-7000-8000-000000000001","quantity":1}],
             "shippingAddress":{"recipientName":"Alice","line1":"1 Street","city":"Grenoble",
                                "postalCode":"38000","countryCode":"FR"}}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService orderService;

    @MockitoBean
    IdempotencyService idempotencyService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void an_idempotency_key_is_mandatory_to_place_an_order() throws Exception {
        mvc.perform(post("/api/v1/orders")
                        .with(customer("alice-sub"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(idempotencyService, orderService);
    }

    @Test
    void every_invalid_field_is_reported_with_its_path() throws Exception {
        String invalidOrder = """
                {"items":[{"productId":"0192f0a0-0000-7000-8000-000000000001"}],
                 "shippingAddress":{"recipientName":"","line1":"1 Street","city":"Grenoble",
                                    "postalCode":"38000","countryCode":"fr"}}
                """;

        mvc.perform(post("/api/v1/orders")
                        .with(customer("alice-sub"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidOrder))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder(
                                "items[0].quantity", "shippingAddress.countryCode", "shippingAddress.recipientName")));
    }

    @Test
    void a_malformed_json_body_is_rejected_without_leaking_parser_details() throws Exception {
        mvc.perform(post("/api/v1/orders")
                        .with(customer("alice-sub"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [ oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("The request body is missing or is not valid JSON."));
    }

    @Test
    void a_retried_request_returns_the_original_order_and_says_so() throws Exception {
        OrderResponse original = OrderResponse.from(sampleOrder());
        String key = UUID.randomUUID().toString();
        when(idempotencyService.execute(anyString(), eq(key), any(), eq(201), eq(OrderResponse.class), any()))
                .thenReturn(new IdempotentResult<>(original, true));

        mvc.perform(post("/api/v1/orders")
                        .with(customer("alice-sub"))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/" + original.id()))
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.orderNumber").value("KO-00000001"));

        // Keys are scoped per customer: two customers may legitimately send the same key.
        verify(idempotencyService)
                .execute(eq("orders.place:alice-sub"), eq(key), any(), eq(201), eq(OrderResponse.class), any());
    }

    private static Order sampleOrder() {
        return Order.place(
                "KO-00000001",
                UUID.randomUUID(),
                new ShippingAddress("Alice", "1 Street", null, "Grenoble", "38000", "FR"),
                List.of(new NewOrderLine(UUID.randomUUID(), "TENT-1", "Tent", Money.of("10.00", "EUR"), 1)),
                Instant.parse("2026-10-01T10:00:00Z"));
    }
}
