package com.warehouse.inventory.dto;

import com.warehouse.inventory.model.MovementType;

public record MovementRequest(
        MovementType type,
        Integer quantity,
        String reason) {
}
