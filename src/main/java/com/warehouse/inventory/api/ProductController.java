package com.warehouse.inventory.api;

import com.warehouse.inventory.dto.CreateProductRequest;
import com.warehouse.inventory.dto.UpdateProductRequest;
import com.warehouse.inventory.model.Product;
import com.warehouse.inventory.service.ProductService;

public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    public void register(Router router) {
        router.get("/api/products", this::list);
        router.post("/api/products", this::create);
        router.get("/api/products/{id}", this::get);
        router.put("/api/products/{id}", this::update);
        router.delete("/api/products/{id}", this::archive);
    }

    private Response list(Request request) {
        return Response.ok(service.list(
                request.query("q").orElse(null),
                request.query("category").orElse(null),
                request.queryBoolean("lowStock"),
                request.queryInt("limit"),
                request.queryInt("offset")));
    }

    private Response create(Request request) {
        Product product = service.create(request.bodyAs(CreateProductRequest.class));
        return Response.created(product, "/api/products/" + product.id());
    }

    private Response get(Request request) {
        return Response.ok(service.get(request.pathLong("id")));
    }

    private Response update(Request request) {
        return Response.ok(service.update(request.pathLong("id"), request.bodyAs(UpdateProductRequest.class)));
    }

    private Response archive(Request request) {
        service.archive(request.pathLong("id"));
        return Response.noContent();
    }
}
