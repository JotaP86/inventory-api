package com.warehouse.inventory.model;

import java.time.Instant;

public record StockMovement(
        long id,
        long productId,
        MovementType type,
        int quantity,
        int stockAfter,
        String reason,
        Instant createdAt) {
}
