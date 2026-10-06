package com.kestrel.commerce.catalog.domain;

public enum ProductStatus {
    /** Visible in the shop and can be ordered. */
    ACTIVE,
    /** Hidden from the shop and cannot be ordered. Kept for history: existing orders still reference it. */
    ARCHIVED
}
