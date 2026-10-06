package com.kestrel.commerce.payment.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookSignatureVerifierTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdefghij";
    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final String PAYLOAD = "{\"id\":\"evt_1\",\"type\":\"payment.succeeded\"}";

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(
            new PaymentWebhookProperties(SECRET, Duration.ofMinutes(5)), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void a_correctly_signed_payload_is_accepted() {
        String header = WebhookSignatureVerifier.signatureHeader(SECRET, NOW.getEpochSecond(), PAYLOAD);

        assertThatCode(() -> verifier.verify(PAYLOAD, header)).doesNotThrowAnyException();
    }

    @Test
    void a_tampered_payload_is_rejected() {
        String header = WebhookSignatureVerifier.signatureHeader(SECRET, NOW.getEpochSecond(), PAYLOAD);

        assertInvalid(PAYLOAD.replace("evt_1", "evt_2"), header);
    }

    @Test
    void a_payload_signed_with_another_secret_is_rejected() {
        String header = WebhookSignatureVerifier.signatureHeader(
                "attacker-secret-0123456789abcdefgh", NOW.getEpochSecond(), PAYLOAD);

        assertInvalid(PAYLOAD, header);
    }

    @Test
    void an_old_signature_is_rejected_to_prevent_replays() {
        long sixMinutesAgo = NOW.minus(Duration.ofMinutes(6)).getEpochSecond();
        String header = WebhookSignatureVerifier.signatureHeader(SECRET, sixMinutesAgo, PAYLOAD);

        assertInvalid(PAYLOAD, header);
    }

    @Test
    void a_signature_within_the_tolerance_is_accepted() {
        long fourMinutesAgo = NOW.minus(Duration.ofMinutes(4)).getEpochSecond();
        String header = WebhookSignatureVerifier.signatureHeader(SECRET, fourMinutesAgo, PAYLOAD);

        assertThatCode(() -> verifier.verify(PAYLOAD, header)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"garbage", "t=abc,v1=00", "t=1791000000", "v1=00ff", "t=1791000000,v1=not-hex"})
    void malformed_headers_are_rejected(String header) {
        assertInvalid(PAYLOAD, header);
    }

    private void assertInvalid(String payload, String header) {
        assertThatThrownBy(() -> verifier.verify(payload, header))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }
}
