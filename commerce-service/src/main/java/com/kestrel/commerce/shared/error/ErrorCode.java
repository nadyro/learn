package com.kestrel.commerce.shared.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in the {@code code} field of every error response.
 *
 * <p>Clients (web, mobile, other services) branch on these codes, so treat them as part of the public API: never
 * rename or remove one without a deprecation period. Adding new codes is fine.
 */
public enum ErrorCode {
    // Generic
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Resource was modified concurrently"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Request conflicts with existing data"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),

    // Idempotency
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency key reused with a different request"),
    IDEMPOTENCY_KEY_IN_FLIGHT(HttpStatus.CONFLICT, "A request with this idempotency key is already being processed"),

    // Catalog
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product not found"),
    SKU_ALREADY_EXISTS(HttpStatus.CONFLICT, "SKU already exists"),
    PRODUCT_NOT_PURCHASABLE(HttpStatus.UNPROCESSABLE_CONTENT, "Product cannot be purchased"),

    // Inventory
    INVENTORY_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Inventory item not found"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Insufficient stock"),
    INVALID_STOCK_ADJUSTMENT(HttpStatus.UNPROCESSABLE_CONTENT, "Invalid stock adjustment"),

    // Orders
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found"),
    DUPLICATE_ORDER_LINE(HttpStatus.UNPROCESSABLE_CONTENT, "Duplicate product in order"),
    MIXED_CURRENCIES(HttpStatus.UNPROCESSABLE_CONTENT, "Products in an order must share the same currency"),
    INVALID_ORDER_STATE(HttpStatus.CONFLICT, "Operation not allowed in the current order state"),

    // Payments
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "Invalid webhook signature"),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "Paid amount does not match the order total");

    private static final String TYPE_BASE_URI = "https://api.kestrel-outfitters.example/problems/";

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** RFC 9457 {@code type} URI, e.g. {@code https://api.kestrel-outfitters.example/problems/insufficient-stock}. */
    public String typeUri() {
        return TYPE_BASE_URI + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 400 -> MALFORMED_REQUEST;
            case 401 -> UNAUTHENTICATED;
            case 403 -> ACCESS_DENIED;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> DATA_CONFLICT;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 500 ? INTERNAL_ERROR : MALFORMED_REQUEST;
        };
    }
}
