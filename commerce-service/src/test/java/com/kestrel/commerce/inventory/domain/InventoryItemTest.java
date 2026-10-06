package com.kestrel.commerce.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kestrel.commerce.shared.error.DomainException;
import com.kestrel.commerce.shared.error.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryItemTest {

    private final InventoryItem item = new InventoryItem(UUID.randomUUID(), 10);

    @Test
    void reserving_reduces_the_available_stock_but_not_the_stock_on_hand() {
        item.reserve(3);

        assertThat(item.getOnHand()).isEqualTo(10);
        assertThat(item.getReserved()).isEqualTo(3);
        assertThat(item.available()).isEqualTo(7);
    }

    @Test
    void releasing_makes_reserved_units_available_again() {
        item.reserve(3);
        item.release(2);

        assertThat(item.available()).isEqualTo(9);
    }

    @Test
    void fulfilling_removes_reserved_units_from_the_warehouse() {
        item.reserve(4);
        item.fulfil(4);

        assertThat(item.getOnHand()).isEqualTo(6);
        assertThat(item.getReserved()).isZero();
        assertThat(item.available()).isEqualTo(6);
    }

    @Test
    void more_than_the_available_stock_cannot_be_reserved() {
        item.reserve(8);

        assertThat(item.canReserve(3)).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> item.reserve(3));
    }

    @Test
    void units_that_are_not_reserved_cannot_be_released_or_fulfilled() {
        item.reserve(1);

        assertThatIllegalStateException().isThrownBy(() -> item.release(2));
        assertThatIllegalStateException().isThrownBy(() -> item.fulfil(2));
    }

    @Test
    void quantities_must_be_positive() {
        assertThatIllegalArgumentException().isThrownBy(() -> item.reserve(0));
        assertThatIllegalArgumentException().isThrownBy(() -> item.release(-1));
    }

    @Test
    void adjustments_cannot_remove_stock_promised_to_orders() {
        item.reserve(8);

        assertThatThrownBy(() -> item.adjust(-3))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_STOCK_ADJUSTMENT);

        item.adjust(-2);
        assertThat(item.getOnHand()).isEqualTo(8);
    }

    @Test
    void zero_adjustments_are_rejected() {
        assertThatThrownBy(() -> item.adjust(0)).isInstanceOf(DomainException.class);
    }
}
