package com.warehouse.inventory.exception;

public class InsufficientStockException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public InsufficientStockException(String sku, int available, int requested) {
        super("Insufficient stock for " + sku + ": available " + available + ", requested " + requested);
    }
}
