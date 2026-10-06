package com.kestrel.commerce.inventory.domain;

public enum AdjustmentReason {
    /** Stock recorded when the product was created. */
    INITIAL_STOCK,
    /** New delivery from a supplier. */
    RESTOCK,
    /** Customer return put back on the shelf. */
    CUSTOMER_RETURN,
    /** Units found damaged and written off. */
    DAMAGED,
    /** Correction after a physical inventory count. */
    INVENTORY_COUNT
}
