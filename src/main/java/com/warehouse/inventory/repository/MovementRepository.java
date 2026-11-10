package com.warehouse.inventory.repository;

import com.warehouse.inventory.model.MovementType;
import com.warehouse.inventory.model.StockMovement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MovementRepository {

    private static final String COLUMNS =
            "id, product_id, type, quantity, stock_after, reason, created_at";

    public long insert(Connection c, long productId, MovementType type, int quantity, int stockAfter,
                       String reason, Instant now) throws SQLException {
        String sql = "INSERT INTO stock_movements (product_id, type, quantity, stock_after, reason, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, productId);
            ps.setString(2, type.name());
            ps.setInt(3, quantity);
            ps.setInt(4, stockAfter);
            ps.setString(5, reason);
            ps.setObject(6, Sql.toDb(now));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong("id");
            }
        }
    }

    public List<StockMovement> findByProduct(Connection c, long productId, int limit, int offset)
            throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM stock_movements WHERE product_id = ? "
                + "ORDER BY id DESC LIMIT ? OFFSET ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, productId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            return collect(ps);
        }
    }

    public List<StockMovement> findRecent(Connection c, int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM stock_movements ORDER BY id DESC LIMIT ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            return collect(ps);
        }
    }

    private static List<StockMovement> collect(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<StockMovement> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new StockMovement(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        MovementType.valueOf(rs.getString("type")),
                        rs.getInt("quantity"),
                        rs.getInt("stock_after"),
                        rs.getString("reason"),
                        Sql.instant(rs, "created_at")));
            }
            return result;
        }
    }
}
