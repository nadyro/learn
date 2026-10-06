package com.kestrel.commerce.shared.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for expected business errors (an order that cannot be cancelled, a SKU that already exists, ...).
 *
 * <p>These are translated to RFC 9457 problem responses by {@code GlobalExceptionHandler} using the {@link ErrorCode}.
 * The message is returned to the client as {@code detail}, so it must be understandable by an API consumer and must
 * never contain sensitive data. Anything in {@link #properties()} is added to the response body as extra fields.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> properties;

    public DomainException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public DomainException(ErrorCode errorCode, String message, Map<String, Object> properties) {
        super(message);
        this.errorCode = errorCode;
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
