package com.warehouse.inventory.config;

import com.warehouse.inventory.exception.DataAccessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static Database from(AppConfig config) {
        return new Database(config.dbUrl(), config.dbUser(), config.dbPassword());
    }

    public void initSchema() {
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("schema.sql not found on classpath");
            }
            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection connection = open(); Statement statement = connection.createStatement()) {
                statement.execute(script);
            }
        } catch (IOException | SQLException e) {
            throw new DataAccessException("Could not initialise schema", e);
        }
    }

    public <T> T read(SqlWork<T> work) {
        try (Connection connection = open()) {
            return work.apply(connection);
        } catch (SQLException e) {
            throw new DataAccessException("Database query failed", e);
        }
    }

    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                rollback(connection, e);
                throw new DataAccessException("Database transaction failed", e);
            } catch (RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not open database connection", e);
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static void rollback(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }
}
