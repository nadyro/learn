package com.kestrel.commerce.payment.api;

import com.kestrel.commerce.payment.application.PaymentEvent;
import com.kestrel.commerce.payment.application.PaymentWebhookService;
import com.kestrel.commerce.payment.application.WebhookOutcome;
import com.kestrel.commerce.payment.application.WebhookSignatureVerifier;
import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Receives payment notifications from our payment service provider.
 *
 * <p>Not protected by a JWT (the provider cannot get one) but by an HMAC signature, see
 * {@link WebhookSignatureVerifier}. Any 2xx response tells the provider to stop retrying; any other status makes it
 * retry with exponential backoff.
 */
@RestController
@RequestMapping("/api/v1/webhooks/payments")
@Tag(name = "Webhooks", description = "Callbacks from external providers")
@SecurityRequirements // authenticated by signature, not by bearer token
public class PaymentWebhookController {

    static final String SIGNATURE_HEADER = "Payment-Signature";

    private final WebhookSignatureVerifier signatureVerifier;
    private final PaymentWebhookService webhookService;
    private final JsonMapper jsonMapper;

    public PaymentWebhookController(
            WebhookSignatureVerifier signatureVerifier, PaymentWebhookService webhookService, JsonMapper jsonMapper) {
        this.signatureVerifier = signatureVerifier;
        this.webhookService = webhookService;
        this.jsonMapper = jsonMapper;
    }

    public record WebhookResponse(String eventId, WebhookOutcome outcome) {}

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Payment provider webhook")
    public WebhookResponse receive(
            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature, @RequestBody String payload) {
        // Verify the raw body first: never parse, let alone act on, unauthenticated input.
        signatureVerifier.verify(payload, signature);
        PaymentEvent event = parse(payload);
        return new WebhookResponse(event.id(), webhookService.process(event));
    }

    private PaymentEvent parse(String payload) {
        try {
            return jsonMapper.readValue(payload, PaymentEvent.class);
        } catch (JacksonException e) {
            throw new DomainException(ErrorCode.MALFORMED_REQUEST, "Webhook payload is not a valid payment event.");
        }
    }
}
