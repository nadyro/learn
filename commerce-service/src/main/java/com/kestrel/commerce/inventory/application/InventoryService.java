package com.kestrel.commerce.inventory.application;

import com.kestrel.commerce.inventory.domain.AdjustmentReason;
import com.kestrel.commerce.inventory.domain.InsufficientStockException;
import com.kestrel.commerce.inventory.domain.InsufficientStockException.Shortage;
import com.kestrel.commerce.inventory.domain.InventoryItem;
import com.kestrel.commerce.inventory.domain.InventoryItemRepository;
import com.kestrel.commerce.inventory.domain.StockAdjustment;
import com.kestrel.commerce.inventory.domain.StockAdjustmentRepository;
import com.kestrel.commerce.shared.error.ErrorCode;
import com.kestrel.commerce.shared.error.NotFoundException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the inventory module.
 *
 * <p>Methods used by other modules ({@code reserve}, {@code release}, {@code fulfil}, {@code registerProduct}) require
 * an existing transaction ({@link Propagation#MANDATORY}): stock must change atomically with the order or product
 * that caused the change.
 */
@Service
@Transactional
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryItemRepository inventoryItems;
    private final StockAdjustmentRepository stockAdjustments;
    private final Clock clock;

    public InventoryService(
            InventoryItemRepository inventoryItems, StockAdjustmentRepository stockAdjustments, Clock clock) {
        this.inventoryItems = inventoryItems;
        this.stockAdjustments = stockAdjustments;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registerProduct(UUID productId, int initialStock, String performedBy) {
        inventoryItems.save(new InventoryItem(productId, initialStock));
        if (initialStock > 0) {
            stockAdjustments.save(new StockAdjustment(
                    productId,
                    initialStock,
                    AdjustmentReason.INITIAL_STOCK,
                    null,
                    performedBy,
                    initialStock,
                    clock.instant()));
        }
    }

    @Transactional(readOnly = true)
    public InventoryItem getInventory(UUID productId) {
        return inventoryItems
                .findById(productId)
                .orElseThrow(
                        () -> new NotFoundException(ErrorCode.INVENTORY_ITEM_NOT_FOUND, "Inventory item", productId));
    }

    public InventoryItem adjustStock(
            UUID productId, int delta, AdjustmentReason reason, String note, String performedBy) {
        InventoryItem item = inventoryItems
                .findForUpdate(productId)
                .orElseThrow(
                        () -> new NotFoundException(ErrorCode.INVENTORY_ITEM_NOT_FOUND, "Inventory item", productId));
        item.adjust(delta);
        stockAdjustments.save(
                new StockAdjustment(productId, delta, reason, note, performedBy, item.getOnHand(), clock.instant()));
        log.info("Stock of product {} adjusted by {} ({}) by {}", productId, delta, reason, performedBy);
        return item;
    }

    /**
     * Reserves stock for all lines, or for none of them.
     *
     * @throws InsufficientStockException listing every line that cannot be served
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(List<StockLine> lines) {
        Map<UUID, InventoryItem> items = lockItems(lines);
        List<Shortage> shortages = new ArrayList<>();
        for (StockLine line : lines) {
            InventoryItem item = items.get(line.productId());
            int available = item == null ? 0 : item.available();
            if (available < line.quantity()) {
                shortages.add(new Shortage(line.productId(), line.quantity(), available));
            }
        }
        if (!shortages.isEmpty()) {
            throw new InsufficientStockException(shortages);
        }
        lines.forEach(line -> items.get(line.productId()).reserve(line.quantity()));
    }

    /** Gives reserved units back to the available stock, e.g. when an order is cancelled. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(List<StockLine> lines) {
        Map<UUID, InventoryItem> items = lockItems(lines);
        lines.forEach(line -> requireItem(items, line).release(line.quantity()));
    }

    /** Removes reserved units from the warehouse stock when an order ships. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void fulfil(List<StockLine> lines) {
        Map<UUID, InventoryItem> items = lockItems(lines);
        lines.forEach(line -> requireItem(items, line).fulfil(line.quantity()));
    }

    private Map<UUID, InventoryItem> lockItems(List<StockLine> lines) {
        List<UUID> productIds = lines.stream().map(StockLine::productId).toList();
        return inventoryItems.findAllForUpdate(productIds).stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity()));
    }

    private static InventoryItem requireItem(Map<UUID, InventoryItem> items, StockLine line) {
        InventoryItem item = items.get(line.productId());
        if (item == null) {
            throw new IllegalStateException("No inventory item for product " + line.productId());
        }
        return item;
    }
}
