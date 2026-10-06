package com.kestrel.commerce.shared.idempotency;

/**
 * @param response the response body, freshly computed or replayed from the first request
 * @param replayed {@code true} when the request was a retry and the stored response was returned
 */
public record IdempotentResult<T>(T response, boolean replayed) {}
