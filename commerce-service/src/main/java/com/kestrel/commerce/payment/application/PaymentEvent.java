package com.kestrel.commerce.payment.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Webhook payload sent by the payment provider.
 *
 * <pre>{@code
 * {
 *   "id": "evt_01J9Z3...",
 *   "type": "payment.succeeded",
 *   "createdAt": "2026-10-01T09:30:00Z",
 *   "data": { "orderId": "...", "paymentReference": "pay_123", "amount": 129.90, "currency": "EUR" }
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true) // providers add fields over time, that must not break us
public record PaymentEvent(String id, String type, Instant createdAt, Data data) {

    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(UUID orderId, String paymentReference, BigDecimal amount, String currency) {}
}
