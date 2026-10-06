package com.kestrel.commerce.support;

import com.kestrel.commerce.payment.application.WebhookSignatureVerifier;
import java.time.Instant;

/** Signs webhook payloads the way our payment provider does. */
public final class WebhookSigner {

    public static final String TEST_SECRET = "test-webhook-signing-secret-0123456789abcdef";

    private WebhookSigner() {}

    public static String sign(String payload) {
        return WebhookSignatureVerifier.signatureHeader(
                TEST_SECRET, Instant.now().getEpochSecond(), payload);
    }

    public static String paymentSucceeded(String eventId, Object orderId, String amount, String currency) {
        return """
                {"id":"%s","type":"payment.succeeded","createdAt":"2026-10-01T09:30:00Z",
                 "data":{"orderId":"%s","paymentReference":"pay_%s","amount":%s,"currency":"%s"}}
                """.formatted(eventId, orderId, eventId, amount, currency);
    }
}
