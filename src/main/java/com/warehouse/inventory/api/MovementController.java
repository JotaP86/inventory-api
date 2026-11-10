package com.warehouse.inventory.api;

import com.warehouse.inventory.dto.MovementRequest;
import com.warehouse.inventory.model.StockMovement;
import com.warehouse.inventory.service.StockService;

public class MovementController {

    private final StockService service;

    public MovementController(StockService service) {
        this.service = service;
    }

    public void register(Router router) {
        router.post("/api/products/{id}/movements", this::create);
        router.get("/api/products/{id}/movements", this::history);
        router.get("/api/movements", this::recent);
    }

    private Response create(Request request) {
        long productId = request.pathLong("id");
        StockMovement movement = service.register(productId, request.bodyAs(MovementRequest.class));
        return Response.created(movement, "/api/products/" + productId + "/movements");
    }

    private Response history(Request request) {
        return Response.ok(service.history(
                request.pathLong("id"), request.queryInt("limit"), request.queryInt("offset")));
    }

    private Response recent(Request request) {
        return Response.ok(service.recent(request.queryInt("limit")));
    }
}
