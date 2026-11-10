package com.warehouse.inventory.api;

import java.util.Map;

public record Response(int status, Object body, Map<String, String> headers) {

    public static Response ok(Object body) {
        return new Response(200, body, Map.of());
    }

    public static Response created(Object body, String location) {
        return new Response(201, body, Map.of("Location", location));
    }

    public static Response noContent() {
        return new Response(204, null, Map.of());
    }
}
