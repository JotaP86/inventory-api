package com.warehouse.inventory.exception;

public class NotFoundException extends ApiException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(404, message);
    }
}
