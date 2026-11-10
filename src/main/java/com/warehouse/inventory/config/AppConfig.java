package com.warehouse.inventory.config;

public record AppConfig(int port, String dbUrl, String dbUser, String dbPassword, boolean seedDemoData) {

    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_DB_URL = "jdbc:h2:file:./data/inventory;DB_CLOSE_DELAY=-1";

    public static AppConfig fromEnvironment() {
        return new AppConfig(
                intValue("PORT", DEFAULT_PORT),
                value("DB_URL", DEFAULT_DB_URL),
                value("DB_USER", "sa"),
                value("DB_PASSWORD", ""),
                Boolean.parseBoolean(value("SEED_DEMO_DATA", "false")));
    }

    private static String value(String name, String fallback) {
        String raw = System.getenv(name);
        return raw == null || raw.isBlank() ? fallback : raw;
    }

    private static int intValue(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer, got: " + raw);
        }
    }
}
