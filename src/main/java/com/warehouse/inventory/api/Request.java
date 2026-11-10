package com.warehouse.inventory.api;

import com.warehouse.inventory.exception.ValidationException;
import java.util.Map;
import java.util.Optional;

public final class Request {

    private final Map<String, String> pathParams;
    private final Map<String, String> queryParams;
    private final byte[] body;

    public Request(Map<String, String> pathParams, Map<String, String> queryParams, byte[] body) {
        this.pathParams = pathParams;
        this.queryParams = queryParams;
        this.body = body;
    }

    public long pathLong(String name) {
        try {
            return Long.parseLong(pathParams.get(name));
        } catch (NumberFormatException e) {
            throw new ValidationException("Path parameter '" + name + "' must be a number");
        }
    }

    public Optional<String> query(String name) {
        return Optional.ofNullable(queryParams.get(name));
    }

    public Integer queryInt(String name) {
        String raw = queryParams.get(name);
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Query parameter '" + name + "' must be a number");
        }
    }

    public boolean queryBoolean(String name) {
        String raw = queryParams.get(name);
        if (raw == null || raw.isBlank()) {
            return false;
        }
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        throw new ValidationException("Query parameter '" + name + "' must be true or false");
    }

    public <T> T bodyAs(Class<T> type) {
        return Json.read(body, type);
    }
}
