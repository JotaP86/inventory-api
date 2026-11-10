package com.warehouse.inventory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record Product(
        long id,
        String sku,
        String name,
        String category,
        BigDecimal price,
        int stock,
        int minStock,
        Instant createdAt,
        Instant updatedAt) {
    @JsonProperty("lowStock")
    public boolean isLowStock() {
        return minStock > 0 && stock <= minStock;
    }
}
