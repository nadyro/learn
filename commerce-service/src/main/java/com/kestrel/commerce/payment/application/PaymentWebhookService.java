package com.kestrel.commerce.payment.application;

import com.kestrel.commerce.order.application.OrderService;
import com.kestrel.commerce.payment.domain.ProcessedWebhookEvent;
import com.kestrel.commerce.payment.domain.ProcessedWebhookEventRepository;
import com.kestrel.commerce.shared.domain.Money;
import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Applies payment provider events to orders.
 *
 * <p>Providers deliver webhooks <b>at least once</b> and may retry for days, so processing is idempotent: the event ID
 * is stored in the same transaction as the order change, and an event seen before is acknowledged without effect.
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final ProcessedWebhookEventRepository processedEvents;
    private final OrderService orderService;
    private final Clock clock;

    public PaymentWebhookService(
            ProcessedWebhookEventRepository processedEvents, OrderService orderService, Clock clock) {
        this.processedEvents = processedEvents;
        this.orderService = orderService;
        this.clock = clock;
    }

    @Transactional
    public WebhookOutcome process(PaymentEvent event) {
        validate(event);
        if (processedEvents.existsById(event.id())) {
            log.info("Payment webhook {} already processed, ignoring redelivery", event.id());
            return WebhookOutcome.DUPLICATE;
        }

        WebhookOutcome outcome;
        if (PaymentEvent.PAYMENT_SUCCEEDED.equals(event.type())) {
            PaymentEvent.Data data = event.data();
            orderService.confirmPayment(
                    data.orderId(), data.paymentReference(), new Money(data.amount(), data.currency()));
            outcome = WebhookOutcome.PROCESSED;
        } else {
            log.info("Ignoring payment webhook {} of unsupported type {}", event.id(), event.type());
            outcome = WebhookOutcome.IGNORED;
        }

        processedEvents.save(new ProcessedWebhookEvent(event.id(), event.type(), clock.instant()));
        return outcome;
    }

    private static void validate(PaymentEvent event) {
        if (!StringUtils.hasText(event.id()) || !StringUtils.hasText(event.type())) {
            throw malformed("Webhook event must have an 'id' and a 'type'.");
        }
        if (PaymentEvent.PAYMENT_SUCCEEDED.equals(event.type())) {
            PaymentEvent.Data data = event.data();
            if (data == null
                    || data.orderId() == null
                    || !StringUtils.hasText(data.paymentReference())
                    || data.amount() == null
                    || data.currency() == null) {
                throw malformed("payment.succeeded events need orderId, paymentReference, amount and currency.");
            }
        }
    }

    private static DomainException malformed(String message) {
        return new DomainException(ErrorCode.MALFORMED_REQUEST, message);
    }
}
