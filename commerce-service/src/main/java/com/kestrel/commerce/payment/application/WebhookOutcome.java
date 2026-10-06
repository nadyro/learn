package com.kestrel.commerce.payment.application;

public enum WebhookOutcome {
    /** The event was handled. */
    PROCESSED,
    /** The event was already handled before: the provider redelivered it. */
    DUPLICATE,
    /** We don't act on this event type. Acknowledged so the provider stops retrying. */
    IGNORED
}
