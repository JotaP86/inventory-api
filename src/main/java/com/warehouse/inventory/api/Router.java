package com.warehouse.inventory.api;

import com.warehouse.inventory.exception.ApiException;
import com.warehouse.inventory.exception.NotFoundException;
import com.warehouse.inventory.exception.ValidationException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Router {

    @FunctionalInterface
    public interface Handler {
        Response handle(Request request);
    }

    private record Route(String method, List<String> segments, Handler handler) {
    }

    private final List<Route> routes = new ArrayList<>();

    public void get(String pattern, Handler handler) {
        add("GET", pattern, handler);
    }

    public void post(String pattern, Handler handler) {
        add("POST", pattern, handler);
    }

    public void put(String pattern, Handler handler) {
        add("PUT", pattern, handler);
    }

    public void delete(String pattern, Handler handler) {
        add("DELETE", pattern, handler);
    }

    private void add(String method, String pattern, Handler handler) {
        routes.add(new Route(method, split(pattern), handler));
    }

    public Response dispatch(String method, URI uri, byte[] body) {
        List<String> segments = split(uri.getPath());
        Map<String, String> query = parseQuery(uri.getRawQuery());
        boolean pathMatched = false;

        for (Route route : routes) {
            Map<String, String> pathParams = match(route.segments(), segments);
            if (pathParams == null) {
                continue;
            }
            pathMatched = true;
            if (route.method().equals(method)) {
                return route.handler().handle(new Request(pathParams, query, body));
            }
        }
        if (pathMatched) {
            throw new ApiException(405, "Method " + method + " not allowed for " + uri.getPath());
        }
        throw new NotFoundException("No route for " + uri.getPath());
    }

    private static List<String> split(String path) {
        List<String> segments = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                segments.add(part);
            }
        }
        return segments;
    }

    private static Map<String, String> match(List<String> pattern, List<String> actual) {
        if (pattern.size() != actual.size()) {
            return null;
        }
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < pattern.size(); i++) {
            String expected = pattern.get(i);
            if (expected.startsWith("{") && expected.endsWith("}")) {
                params.put(expected.substring(1, expected.length() - 1), actual.get(i));
            } else if (!expected.equals(actual.get(i))) {
                return null;
            }
        }
        return params;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = decode(equals < 0 ? pair : pair.substring(0, equals));
            String value = equals < 0 ? "" : decode(pair.substring(equals + 1));
            result.putIfAbsent(key, value);
        }
        return result;
    }

    private static String decode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Malformed query string");
        }
    }
}
