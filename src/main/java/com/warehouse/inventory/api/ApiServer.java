package com.warehouse.inventory.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.warehouse.inventory.exception.ApiException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ApiServer {

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());
    private static final int MAX_BODY_BYTES = 1_048_576;
    private static final int WORKER_THREADS = 8;

    private final HttpServer server;
    private final ExecutorService executor;
    private final Router router;

    public ApiServer(int port, Router router) throws IOException {
        this.router = router;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.executor = Executors.newFixedThreadPool(WORKER_THREADS);
        this.server.setExecutor(executor);
        this.server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
        LOG.info("Listening on port " + port());
    }

    public void stop() {
        server.stop(1);
        executor.shutdown();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        long started = System.nanoTime();
        int status;
        try {
            byte[] body = readBody(exchange);
            Response response = router.dispatch(exchange.getRequestMethod(), exchange.getRequestURI(), body);
            status = send(exchange, response);
        } catch (ApiException e) {
            status = send(exchange, new Response(e.status(), ApiError.from(e), Map.of()));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unhandled error", e);
            status = send(exchange, new Response(500, ApiError.internal(), Map.of()));
        } finally {
            exchange.close();
        }
        long millis = (System.nanoTime() - started) / 1_000_000;
        LOG.info(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " -> " + status
                + " (" + millis + " ms)");
    }

    private static byte[] readBody(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw new ApiException(413, "Request body must not exceed " + MAX_BODY_BYTES + " bytes");
        }
        return body;
    }

    private static int send(HttpExchange exchange, Response response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        response.headers().forEach((name, value) -> exchange.getResponseHeaders().set(name, value));

        if (response.body() == null) {
            exchange.sendResponseHeaders(response.status(), -1);
            return response.status();
        }
        byte[] bytes = Json.write(response.body());
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        return response.status();
    }
}
