package com.kestrel.commerce.order.domain;

public enum CancellationReason {
    /** The customer cancelled the order themselves. */
    CUSTOMER_REQUEST,
    /** Cancelled by customer support or the operations team. */
    BACK_OFFICE,
    /** The order was never paid within the allowed time. */
    PAYMENT_TIMEOUT
}
