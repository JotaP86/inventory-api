package com.warehouse.inventory.service;

import com.warehouse.inventory.exception.ValidationException;
import java.util.ArrayList;
import java.util.List;

final class FieldErrors {

    private final List<String> messages = new ArrayList<>();

    void add(String message) {
        messages.add(message);
    }

    void throwIfAny() {
        if (!messages.isEmpty()) {
            throw new ValidationException("Validation failed", messages);
        }
    }
}
