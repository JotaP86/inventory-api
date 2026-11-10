package com.warehouse.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.warehouse.inventory.config.Database;
import com.warehouse.inventory.dto.CreateProductRequest;
import com.warehouse.inventory.dto.UpdateProductRequest;
import com.warehouse.inventory.exception.ConflictException;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductServiceTest {

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

    private Product create(String sku, String name, String category, String price, int stockUnits, int minStock) {
        return products.create(new CreateProductRequest(sku, name, category, new BigDecimal(price), stockUnits,
                minStock));
    }

    @Test
    void createStoresProductAndRecordsInitialMovement() {
        Product product = create("BOX-1", "Cardboard box", "Packaging", "1.50", 100, 20);

        assertTrue(product.id() > 0);
        assertEquals("BOX-1", product.sku());
        assertEquals(100, product.stock());
        assertEquals(new BigDecimal("1.50"), product.price());

        List<StockMovement> history = stock.history(product.id(), null, null);
        assertEquals(1, history.size());
        assertEquals(MovementType.ENTRY, history.get(0).type());
        assertEquals(100, history.get(0).stockAfter());
    }

    @Test
    void createWithoutInitialStockRecordsNoMovement() {
        Product product = products.create(new CreateProductRequest("TAPE", "Tape", null, new BigDecimal("2"), null,
                null));

        assertEquals(0, product.stock());
        assertEquals(new BigDecimal("2.00"), product.price());
        assertTrue(stock.history(product.id(), null, null).isEmpty());
    }

    @Test
    void skuIsNormalisedToUpperCaseAndTrimmed() {
        Product product = create("  pal-eur  ", "Pallet", null, "10", 0, 0);
        assertEquals("PAL-EUR", product.sku());
    }

    @Test
    void duplicateSkuIsRejectedRegardlessOfCase() {
        create("DUP-1", "First", null, "1", 0, 0);
        assertThrows(ConflictException.class, () -> create("dup-1", "Second", null, "1", 0, 0));
    }

    @Test
    void invalidDataReportsEveryProblemAtOnce() {
        ValidationException error = assertThrows(ValidationException.class, () -> products.create(
                new CreateProductRequest("bad sku!", " ", null, new BigDecimal("-1"), -5, null)));

        assertEquals(4, error.details().size());
    }

    @Test
    void priceWithMoreThanTwoDecimalsIsRejected() {
        assertThrows(ValidationException.class, () -> create("P-1", "Name", null, "1.999", 0, 0));
    }

    @Test
    void updateChangesEditableFieldsOnly() {
        Product created = create("UPD-1", "Old name", "Old", "5.00", 10, 2);

        Product updated = products.update(created.id(),
                new UpdateProductRequest("New name", "New", new BigDecimal("6.25"), 4));

        assertEquals("New name", updated.name());
        assertEquals("New", updated.category());
        assertEquals(new BigDecimal("6.25"), updated.price());
        assertEquals(4, updated.minStock());
        assertEquals("UPD-1", updated.sku());
        assertEquals(10, updated.stock());
    }

    @Test
    void updateOfUnknownProductFails() {
        assertThrows(NotFoundException.class,
                () -> products.update(999, new UpdateProductRequest("Name", null, BigDecimal.ONE, 0)));
    }

    @Test
    void archivedProductsDisappearButKeepTheirSku() {
        Product product = create("ARC-1", "To archive", null, "1", 5, 0);

        products.archive(product.id());

        assertThrows(NotFoundException.class, () -> products.get(product.id()));
        assertTrue(products.list(null, null, false, null, null).isEmpty());
        assertThrows(ConflictException.class, () -> create("ARC-1", "Again", null, "1", 0, 0));
        assertThrows(NotFoundException.class, () -> products.archive(product.id()));
    }

    @Test
    void lowStockFilterReturnsOnlyProductsAtOrBelowTheirThreshold() {
        create("OK-1", "Plenty", null, "1", 100, 10);
        create("LOW-1", "At threshold", null, "1", 10, 10);
        create("LOW-2", "Below threshold", null, "1", 3, 10);
        create("NONE-1", "No threshold", null, "1", 0, 0);

        List<Product> low = products.list(null, null, true, null, null);

        assertEquals(List.of("LOW-1", "LOW-2"), low.stream().map(Product::sku).toList());
        assertTrue(low.stream().allMatch(Product::isLowStock));
    }

    @Test
    void searchMatchesNameOrSkuIgnoringCase() {
        create("GLV-L", "Safety gloves", "PPE", "2", 1, 0);
        create("VEST-1", "High visibility vest", "PPE", "3", 1, 0);

        assertEquals(1, products.list("GLOVES", null, false, null, null).size());
        assertEquals(1, products.list("vest-1", null, false, null, null).size());
        assertEquals(2, products.list(null, "ppe", false, null, null).size());
    }

    @Test
    void searchTreatsLikeWildcardsAsLiteralText() {
        create("ONE", "Plain name", null, "1", 1, 0);

        assertTrue(products.list("%", null, false, null, null).isEmpty());
        assertTrue(products.list("_", null, false, null, null).isEmpty());
    }

    @Test
    void paginationLimitsAndSkipsResults() {
        for (int i = 1; i <= 5; i++) {
            create("ITEM-" + i, "Item " + i, null, "1", 0, 0);
        }

        assertEquals(2, products.list(null, null, false, 2, 0).size());
        assertEquals("ITEM-3", products.list(null, null, false, 2, 2).get(0).sku());
        assertThrows(ValidationException.class, () -> products.list(null, null, false, 0, 0));
        assertThrows(ValidationException.class, () -> products.list(null, null, false, 10, -1));
        assertFalse(products.list(null, null, false, 200, 0).isEmpty());
    }
}
