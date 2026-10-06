package com.kestrel.commerce.payment.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Payment provider webhook settings ({@code commerce.payments.webhook.*}).
 *
 * @param signingSecret shared secret used to sign webhooks. A secret: provide it through the
 *     {@code COMMERCE_PAYMENTS_WEBHOOK_SIGNING_SECRET} environment variable, never commit it. The application refuses
 *     to start without it.
 * @param tolerance maximum age of a webhook signature, protects against replayed requests
 */
@Validated
@ConfigurationProperties(prefix = "commerce.payments.webhook")
public record PaymentWebhookProperties(
        @NotBlank @Size(min = 32) String signingSecret,
        @DefaultValue("PT5M") @NotNull Duration tolerance) {

    @Override
    public String toString() {
        // Records print all fields by default: never let a secret end up in a log line.
        return "PaymentWebhookProperties[signingSecret=******, tolerance=" + tolerance + "]";
    }
}
