package com.warehouse.inventory.service;

import com.warehouse.inventory.config.Database;
import com.warehouse.inventory.dto.CreateProductRequest;
import com.warehouse.inventory.dto.UpdateProductRequest;
import com.warehouse.inventory.exception.NotFoundException;
import com.warehouse.inventory.model.MovementType;
import com.warehouse.inventory.model.Product;
import com.warehouse.inventory.repository.MovementRepository;
import com.warehouse.inventory.repository.ProductFilter;
import com.warehouse.inventory.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class ProductService {

    private final Database database;
    private final ProductRepository products;
    private final MovementRepository movements;
    private final Clock clock;

    public ProductService(Database database, ProductRepository products, MovementRepository movements,
                          Clock clock) {
        this.database = database;
        this.products = products;
        this.movements = movements;
        this.clock = clock;
    }

    public Product create(CreateProductRequest request) {
        FieldErrors errors = new FieldErrors();
        String sku = Fields.sku(request.sku(), errors);
        String name = Fields.requiredText("name", request.name(), 120, errors);
        String category = Fields.optionalText("category", request.category(), 60, errors);
        BigDecimal price = Fields.price(request.price(), errors);
        int initialStock = Fields.stock("initialStock", request.initialStock(), 0, errors);
        int minStock = Fields.stock("minStock", request.minStock(), 0, errors);
        errors.throwIfAny();

        Instant now = clock.instant();

        return database.inTransaction(c -> {
            long id = products.insert(c, sku, name, category, price, initialStock, minStock, now);
            if (initialStock > 0) {
                movements.insert(c, id, MovementType.ENTRY, initialStock, initialStock, "Initial stock", now);
            }
            return products.findById(c, id).orElseThrow();
        });
    }

    public Product get(long id) {
        return database.read(c -> products.findById(c, id).orElseThrow(() -> notFound(id)));
    }

    public List<Product> list(String query, String category, boolean lowStockOnly, Integer limit,
                              Integer offset) {
        FieldErrors errors = new FieldErrors();
        int pageSize = Fields.limit(limit, errors);
        int start = Fields.offset(offset, errors);
        errors.throwIfAny();

        ProductFilter filter = new ProductFilter(blankToNull(query), blankToNull(category), lowStockOnly,
                pageSize, start);
        return database.read(c -> products.search(c, filter));
    }

    public Product update(long id, UpdateProductRequest request) {
        FieldErrors errors = new FieldErrors();
        String name = Fields.requiredText("name", request.name(), 120, errors);
        String category = Fields.optionalText("category", request.category(), 60, errors);
        BigDecimal price = Fields.price(request.price(), errors);
        int minStock = Fields.stock("minStock", request.minStock(), 0, errors);
        errors.throwIfAny();

        Instant now = clock.instant();
        return database.inTransaction(c -> {
            if (!products.update(c, id, name, category, price, minStock, now)) {
                throw notFound(id);
            }
            return products.findById(c, id).orElseThrow();
        });
    }

    public void archive(long id) {
        Instant now = clock.instant();
        database.inTransaction(c -> {
            if (!products.archive(c, id, now)) {
                throw notFound(id);
            }
            return null;
        });
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("Product " + id + " not found");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
