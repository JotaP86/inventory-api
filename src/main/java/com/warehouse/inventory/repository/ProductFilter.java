package com.warehouse.inventory.repository;

public record ProductFilter(String query, String category, boolean lowStockOnly, int limit, int offset) {
}
