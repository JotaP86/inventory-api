package com.warehouse.inventory.exception;

public class ConflictException extends ApiException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(409, message);
    }
}
