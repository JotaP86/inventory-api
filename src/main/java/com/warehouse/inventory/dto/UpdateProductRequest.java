package com.warehouse.inventory.dto;

import java.math.BigDecimal;

public record UpdateProductRequest(
        String name,
        String category,
        BigDecimal price,
        Integer minStock) {
}
