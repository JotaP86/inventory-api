package com.warehouse.inventory;

import com.warehouse.inventory.config.Database;
import java.util.UUID;

final class TestSupport {

    private TestSupport() {
    }

    static Database newDatabase() {
        Database database = new Database(urlForNewDatabase(), "sa", "");
        database.initSchema();
        return database;
    }

    static String urlForNewDatabase() {
        return "jdbc:h2:mem:test_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
    }
}
