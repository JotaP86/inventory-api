package com.warehouse.inventory;

import com.warehouse.inventory.api.ApiServer;
import com.warehouse.inventory.api.MovementController;
import com.warehouse.inventory.api.ProductController;
import com.warehouse.inventory.api.Response;
import com.warehouse.inventory.api.Router;
import com.warehouse.inventory.config.AppConfig;
import com.warehouse.inventory.config.Database;
import com.warehouse.inventory.repository.MovementRepository;
import com.warehouse.inventory.repository.ProductRepository;
import com.warehouse.inventory.service.DemoData;
import com.warehouse.inventory.service.ProductService;
import com.warehouse.inventory.service.StockService;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;

public class Application {

    private final ApiServer server;

    public Application(AppConfig config) throws IOException {
        Database database = Database.from(config);
        database.initSchema();

        Clock clock = Clock.systemUTC();
        ProductRepository productRepository = new ProductRepository();
        MovementRepository movementRepository = new MovementRepository();
        ProductService productService = new ProductService(database, productRepository, movementRepository, clock);
        StockService stockService = new StockService(database, productRepository, movementRepository, clock);

        if (config.seedDemoData()) {
            DemoData.loadIfEmpty(productService, stockService);
        }

        Router router = new Router();
        router.get("/api/health", request -> Response.ok(Map.of("status", "UP")));
        new ProductController(productService).register(router);
        new MovementController(stockService).register(router);

        this.server = new ApiServer(config.port(), router);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    public int port() {
        return server.port();
    }
}
