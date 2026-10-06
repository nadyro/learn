package com.kestrel.commerce.inventory.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {}
