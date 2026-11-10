package com.warehouse.inventory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;

final class Fields {

    static final int MAX_STOCK = 100_000_000;
    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 200;

    private static final Pattern SKU = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$");
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    private Fields() {
    }

    static String requiredText(String field, String value, int maxLength, FieldErrors errors) {
        if (value == null || value.isBlank()) {
            errors.add(field + " is required");
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            errors.add(field + " must be at most " + maxLength + " characters");
            return null;
        }
        return trimmed;
    }

    static String optionalText(String field, String value, int maxLength, FieldErrors errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            errors.add(field + " must be at most " + maxLength + " characters");
            return null;
        }
        return trimmed;
    }

    static String sku(String value, FieldErrors errors) {
        String text = requiredText("sku", value, 40, errors);
        if (text == null) {
            return null;
        }
        if (!SKU.matcher(text).matches()) {
            errors.add("sku may only contain letters, digits, '.', '_' and '-'");
            return null;
        }
        return text.toUpperCase(Locale.ROOT);
    }

    static BigDecimal price(BigDecimal value, FieldErrors errors) {
        if (value == null) {
            errors.add("price is required");
            return null;
        }
        if (value.signum() < 0) {
            errors.add("price must not be negative");
            return null;
        }
        if (value.stripTrailingZeros().scale() > 2) {
            errors.add("price must have at most 2 decimal places");
            return null;
        }
        if (value.compareTo(MAX_PRICE) > 0) {
            errors.add("price must not exceed " + MAX_PRICE.toPlainString());
            return null;
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    static int stock(String field, Integer value, int defaultValue, FieldErrors errors) {
        if (value == null) {
            return defaultValue;
        }
        if (value < 0 || value > MAX_STOCK) {
            errors.add(field + " must be between 0 and " + MAX_STOCK);
            return defaultValue;
        }
        return value;
    }

    static int limit(Integer value, FieldErrors errors) {
        if (value == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (value < 1 || value > MAX_PAGE_SIZE) {
            errors.add("limit must be between 1 and " + MAX_PAGE_SIZE);
            return DEFAULT_PAGE_SIZE;
        }
        return value;
    }

    static int offset(Integer value, FieldErrors errors) {
        if (value == null) {
            return 0;
        }
        if (value < 0) {
            errors.add("offset must be 0 or greater");
            return 0;
        }
        return value;
    }
}
