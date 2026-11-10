package com.warehouse.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.warehouse.inventory.config.Database;
import com.warehouse.inventory.dto.CreateProductRequest;
import com.warehouse.inventory.dto.MovementRequest;
import com.warehouse.inventory.exception.InsufficientStockException;
import com.warehouse.inventory.exception.NotFoundException;
import com.warehouse.inventory.exception.ValidationException;
import com.warehouse.inventory.model.MovementType;
import com.warehouse.inventory.model.Product;
import com.warehouse.inventory.model.StockMovement;
import com.warehouse.inventory.repository.MovementRepository;
import com.warehouse.inventory.repository.ProductRepository;
import com.warehouse.inventory.service.ProductService;
import com.warehouse.inventory.service.StockService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StockServiceTest {

    private ProductService products;
    private StockService stock;

    @BeforeEach
    void setUp() {
        Database database = TestSupport.newDatabase();
        ProductRepository productRepository = new ProductRepository();
        MovementRepository movementRepository = new MovementRepository();
        products = new ProductService(database, productRepository, movementRepository, Clock.systemUTC());
        stock = new StockService(database, productRepository, movementRepository, Clock.systemUTC());
    }

    private Product productWithStock(int units) {
        return products.create(new CreateProductRequest("SKU-" + System.nanoTime(), "Test product", null,
                new BigDecimal("1.00"), units, 0));
    }

    private StockMovement move(Product product, MovementType type, int quantity) {
        return stock.register(product.id(), new MovementRequest(type, quantity, null));
    }

    @Test
    void entryIncreasesStock() {
        Product product = productWithStock(10);

        StockMovement movement = move(product, MovementType.ENTRY, 5);

        assertEquals(15, movement.stockAfter());
        assertEquals(15, products.get(product.id()).stock());
    }

    @Test
    void exitDecreasesStock() {
        Product product = productWithStock(10);

        StockMovement movement = stock.register(product.id(), new MovementRequest(MovementType.EXIT, 4, "Order 77"));

        assertEquals(6, movement.stockAfter());
        assertEquals("Order 77", movement.reason());
        assertEquals(6, products.get(product.id()).stock());
    }

    @Test
    void exitOfExactlyTheAvailableStockIsAllowed() {
        Product product = productWithStock(3);
        assertEquals(0, move(product, MovementType.EXIT, 3).stockAfter());
    }

    @Test
    void exitBeyondAvailableStockFailsAndChangesNothing() {
        Product product = productWithStock(3);

        assertThrows(InsufficientStockException.class, () -> move(product, MovementType.EXIT, 4));

        assertEquals(3, products.get(product.id()).stock());
        assertEquals(1, stock.history(product.id(), null, null).size());
    }

    @Test
    void adjustmentSetsStockToTheCountedQuantity() {
        Product product = productWithStock(10);

        assertEquals(7, move(product, MovementType.ADJUSTMENT, 7).stockAfter());
        assertEquals(0, move(product, MovementType.ADJUSTMENT, 0).stockAfter());
        assertEquals(0, products.get(product.id()).stock());
    }

    @Test
    void entryAndExitRequirePositiveQuantity() {
        Product product = productWithStock(10);

        assertThrows(ValidationException.class, () -> move(product, MovementType.ENTRY, 0));
        assertThrows(ValidationException.class, () -> move(product, MovementType.EXIT, -2));
    }

    @Test
    void missingFieldsAreReported() {
        Product product = productWithStock(10);

        ValidationException error = assertThrows(ValidationException.class,
                () -> stock.register(product.id(), new MovementRequest(null, null, null)));

        assertEquals(2, error.details().size());
    }

    @Test
    void movementOnUnknownProductFails() {
        assertThrows(NotFoundException.class,
                () -> stock.register(12345, new MovementRequest(MovementType.ENTRY, 1, null)));
        assertThrows(NotFoundException.class, () -> stock.history(12345, null, null));
    }

    @Test
    void historyIsReturnedNewestFirst() {
        Product product = productWithStock(10);
        move(product, MovementType.EXIT, 2);
        move(product, MovementType.ENTRY, 20);

        List<StockMovement> history = stock.history(product.id(), null, null);

        assertEquals(3, history.size());
        assertEquals(MovementType.ENTRY, history.get(0).type());
        assertEquals(28, history.get(0).stockAfter());
        assertEquals(MovementType.EXIT, history.get(1).type());
        assertEquals(MovementType.ENTRY, history.get(2).type());
    }

    @Test
    void recentMovementsSpanAllProducts() {
        Product first = productWithStock(1);
        Product second = productWithStock(1);
        move(first, MovementType.ENTRY, 1);
        move(second, MovementType.ENTRY, 1);

        List<StockMovement> recent = stock.recent(3);

        assertEquals(3, recent.size());
        assertEquals(second.id(), recent.get(0).productId());
    }

    @Test
    void concurrentExitsNeverSellMoreThanTheStock() throws Exception {
        Product product = productWithStock(10);
        int attempts = 30;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    move(product, MovementType.EXIT, 1);
                    return true;
                } catch (InsufficientStockException e) {
                    return false;
                }
            }));
        }
        start.countDown();

        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertEquals(10, succeeded);
        assertEquals(0, products.get(product.id()).stock());
        assertTrue(stock.history(product.id(), 200, 0).stream()
                .allMatch(movement -> movement.stockAfter() >= 0));
    }
}
