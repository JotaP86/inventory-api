package com.warehouse.inventory.repository;

import com.warehouse.inventory.exception.ConflictException;
import com.warehouse.inventory.model.Product;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductRepository {

    private static final String COLUMNS =
            "id, sku, name, category, price, stock, min_stock, created_at, updated_at";

    public long insert(Connection c, String sku, String name, String category, BigDecimal price,
                       int stock, int minStock, Instant now) throws SQLException {
        String sql = "INSERT INTO products (sku, name, category, price, stock, min_stock, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, sku);
            ps.setString(2, name);
            ps.setString(3, category);
            ps.setBigDecimal(4, price);
            ps.setInt(5, stock);
            ps.setInt(6, minStock);
            ps.setObject(7, Sql.toDb(now));
            ps.setObject(8, Sql.toDb(now));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong("id");
            }
        } catch (SQLException e) {
            if (Sql.UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw new ConflictException("A product with SKU " + sku + " already exists");
            }
            throw e;
        }
    }

    public Optional<Product> findById(Connection c, long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM products WHERE id = ? AND active = TRUE";
        return findOne(c, sql, id);
    }

    public Optional<Product> findByIdForUpdate(Connection c, long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM products WHERE id = ? AND active = TRUE FOR UPDATE";
        return findOne(c, sql, id);
    }

    public List<Product> search(Connection c, ProductFilter filter) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM products WHERE active = TRUE");
        List<Object> params = new ArrayList<>();

        if (filter.query() != null) {
            sql.append(" AND (LOWER(name) LIKE ? ESCAPE '\\' OR LOWER(sku) LIKE ? ESCAPE '\\')");
            String pattern = "%" + Sql.escapeLike(filter.query().toLowerCase()) + "%";
            params.add(pattern);
            params.add(pattern);
        }
        if (filter.category() != null) {
            sql.append(" AND LOWER(category) = ?");
            params.add(filter.category().toLowerCase());
        }
        if (filter.lowStockOnly()) {
            sql.append(" AND min_stock > 0 AND stock <= min_stock");
        }
        sql.append(" ORDER BY name, id LIMIT ? OFFSET ?");
        params.add(filter.limit());
        params.add(filter.offset());

        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Product> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        }
    }

    public boolean update(Connection c, long id, String name, String category, BigDecimal price,
                          int minStock, Instant now) throws SQLException {
        String sql = "UPDATE products SET name = ?, category = ?, price = ?, min_stock = ?, updated_at = ? "
                + "WHERE id = ? AND active = TRUE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, category);
            ps.setBigDecimal(3, price);
            ps.setInt(4, minStock);
            ps.setObject(5, Sql.toDb(now));
            ps.setLong(6, id);
            return ps.executeUpdate() > 0;
        }
    }

    public void updateStock(Connection c, long id, int stock, Instant now) throws SQLException {
        String sql = "UPDATE products SET stock = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, stock);
            ps.setObject(2, Sql.toDb(now));
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    public boolean archive(Connection c, long id, Instant now) throws SQLException {
        String sql = "UPDATE products SET active = FALSE, updated_at = ? WHERE id = ? AND active = TRUE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, Sql.toDb(now));
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    private Optional<Product> findOne(Connection c, String sql, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static Product map(ResultSet rs) throws SQLException {
        return new Product(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getBigDecimal("price"),
                rs.getInt("stock"),
                rs.getInt("min_stock"),
                Sql.instant(rs, "created_at"),
                Sql.instant(rs, "updated_at"));
    }
}
