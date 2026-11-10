package com.warehouse.inventory.service;

import com.warehouse.inventory.config.Database;
import com.warehouse.inventory.dto.MovementRequest;
import com.warehouse.inventory.exception.ConflictException;
import com.warehouse.inventory.exception.InsufficientStockException;
import com.warehouse.inventory.exception.NotFoundException;
import com.warehouse.inventory.exception.ValidationException;
import com.warehouse.inventory.model.MovementType;
import com.warehouse.inventory.model.Product;
import com.warehouse.inventory.model.StockMovement;
import com.warehouse.inventory.repository.MovementRepository;
import com.warehouse.inventory.repository.ProductRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class StockService {

    private static final int MAX_MOVEMENT_QUANTITY = 1_000_000;

    private final Database database;
    private final ProductRepository products;
    private final MovementRepository movements;
    private final Clock clock;

    public StockService(Database database, ProductRepository products, MovementRepository movements,
                        Clock clock) {
        this.database = database;
        this.products = products;
        this.movements = movements;
        this.clock = clock;
    }

    public StockMovement register(long productId, MovementRequest request) {
        FieldErrors errors = new FieldErrors();
        MovementType type = request.type();
        if (type == null) {
            errors.add("type is required (ENTRY, EXIT or ADJUSTMENT)");
        }
        Integer quantity = request.quantity();
        if (quantity == null) {
            errors.add("quantity is required");
        } else if (type == MovementType.ADJUSTMENT && (quantity < 0 || quantity > Fields.MAX_STOCK)) {
            errors.add("quantity must be between 0 and " + Fields.MAX_STOCK + " for an ADJUSTMENT");
        } else if (type != MovementType.ADJUSTMENT && (quantity < 1 || quantity > MAX_MOVEMENT_QUANTITY)) {
            errors.add("quantity must be between 1 and " + MAX_MOVEMENT_QUANTITY);
        }
        String reason = Fields.optionalText("reason", request.reason(), 200, errors);
        errors.throwIfAny();

        Instant now = clock.instant();
        return database.inTransaction(c -> {
            Product product = products.findByIdForUpdate(c, productId)
                    .orElseThrow(() -> new NotFoundException("Product " + productId + " not found"));

            int newStock = switch (type) {
                case ENTRY -> addStock(product, quantity);
                case EXIT -> {
                    if (quantity > product.stock()) {
                        throw new InsufficientStockException(product.sku(), product.stock(), quantity);
                    }
                    yield product.stock() - quantity;
                }
                case ADJUSTMENT -> quantity;
            };

            products.updateStock(c, productId, newStock, now);
            long id = movements.insert(c, productId, type, quantity, newStock, reason, now);
            return new StockMovement(id, productId, type, quantity, newStock, reason, now);
        });
    }

    public List<StockMovement> history(long productId, Integer limit, Integer offset) {
        FieldErrors errors = new FieldErrors();
        int pageSize = Fields.limit(limit, errors);
        int start = Fields.offset(offset, errors);
        errors.throwIfAny();

        return database.read(c -> {
            products.findById(c, productId)
                    .orElseThrow(() -> new NotFoundException("Product " + productId + " not found"));
            return movements.findByProduct(c, productId, pageSize, start);
        });
    }

    public List<StockMovement> recent(Integer limit) {
        FieldErrors errors = new FieldErrors();
        int pageSize = Fields.limit(limit, errors);
        errors.throwIfAny();
        return database.read(c -> movements.findRecent(c, pageSize));
    }

    private static int addStock(Product product, int quantity) {
        long total = (long) product.stock() + quantity;
        if (total > Fields.MAX_STOCK) {
            throw new ConflictException("Stock for " + product.sku() + " cannot exceed " + Fields.MAX_STOCK);
        }
        return (int) total;
    }
}
