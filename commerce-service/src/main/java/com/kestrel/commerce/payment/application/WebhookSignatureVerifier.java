package com.kestrel.commerce.payment.application;

import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Verifies that a webhook really comes from our payment provider.
 *
 * <p>The provider sends a header {@code Payment-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256>} where the HMAC is
 * computed over {@code "<t>.<raw request body>"} with the shared secret. We check:
 *
 * <ol>
 *   <li>the signature, with a constant-time comparison (a plain {@code equals} leaks timing information);
 *   <li>the timestamp, so a captured request cannot be replayed hours later.
 * </ol>
 *
 * The body must be verified exactly as received: parsing and re-serialising the JSON would change the bytes.
 */
@Component
public class WebhookSignatureVerifier {

    static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final Duration tolerance;
    private final Clock clock;

    public WebhookSignatureVerifier(PaymentWebhookProperties properties, Clock clock) {
        this.secret = properties.signingSecret().getBytes(StandardCharsets.UTF_8);
        this.tolerance = properties.tolerance();
        this.clock = clock;
    }

    public void verify(String payload, String signatureHeader) {
        ParsedSignature signature = parse(signatureHeader);

        Instant signedAt = Instant.ofEpochSecond(signature.timestamp());
        Duration age = Duration.between(signedAt, clock.instant()).abs();
        if (age.compareTo(tolerance) > 0) {
            throw invalid("Signature timestamp is outside the allowed tolerance.");
        }

        byte[] expected = sign(secret, signature.timestamp(), payload);
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signature.hexDigest());
        } catch (IllegalArgumentException e) {
            throw invalid("Signature is not valid hexadecimal.");
        }
        if (!MessageDigest.isEqual(expected, provided)) {
            throw invalid("Signature does not match the payload.");
        }
    }

    /**
     * Computes the header value the payment provider sends for a payload. Used by tests;
     * {@code scripts/send-payment-webhook.sh} does the same with openssl.
     */
    public static String signatureHeader(String secret, long timestamp, String payload) {
        byte[] digest = sign(secret.getBytes(StandardCharsets.UTF_8), timestamp, payload);
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }

    private static byte[] sign(byte[] secret, long timestamp, String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Cannot compute webhook signature", e);
        }
    }

    private static ParsedSignature parse(String header) {
        if (header == null || header.isBlank()) {
            throw invalid("Missing signature header.");
        }
        Long timestamp = null;
        String digest = null;
        for (String part : header.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            switch (keyValue[0]) {
                case "t" -> timestamp = parseTimestamp(keyValue[1]);
                case "v1" -> digest = keyValue[1];
                default -> {
                    // unknown elements are ignored to allow the provider to add new signature schemes
                }
            }
        }
        if (timestamp == null || digest == null) {
            throw invalid("Signature header must contain 't' and 'v1'.");
        }
        return new ParsedSignature(timestamp, digest);
    }

    private static Long parseTimestamp(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw invalid("Signature timestamp is not a number.");
        }
    }

    private static DomainException invalid(String reason) {
        return new DomainException(ErrorCode.INVALID_WEBHOOK_SIGNATURE, reason);
    }

    private record ParsedSignature(long timestamp, String hexDigest) {}
}
