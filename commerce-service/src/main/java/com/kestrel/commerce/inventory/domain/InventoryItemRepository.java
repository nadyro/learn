package com.kestrel.commerce.inventory.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    /**
     * Loads and locks ({@code SELECT ... FOR UPDATE}) the inventory rows of several products.
     *
     * <p>Rows are locked <b>in product ID order</b>. If two transactions locked the same rows in a different order they
     * could each wait for the other forever (a deadlock). A consistent order makes that impossible.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId in :productIds order by i.productId")
    List<InventoryItem> findAllForUpdate(Collection<UUID> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId = :productId")
    Optional<InventoryItem> findForUpdate(UUID productId);
}
