package com.warehouse.inventory.dto;

import java.math.BigDecimal;

public record CreateProductRequest(
        String sku,
        String name,
        String category,
        BigDecimal price,
        Integer initialStock,
        Integer minStock) {
}
