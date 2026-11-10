package com.warehouse.inventory.exception;

import java.util.List;

public class ValidationException extends ApiException {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial")
    private final List<String> details;

    public ValidationException(String message) {
        this(message, List.of());
    }

    public ValidationException(String message, List<String> details) {
        super(400, message);
        this.details = List.copyOf(details);
    }

    public List<String> details() {
        return details;
    }
}
