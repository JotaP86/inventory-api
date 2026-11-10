package com.warehouse.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.inventory.config.AppConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Application application;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        application = new Application(new AppConfig(0, TestSupport.urlForNewDatabase(), "sa", "", false));
        application.start();
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + application.port();
    }

    @AfterEach
    void stopServer() {
        application.stop();
    }

    private HttpResponse<String> call(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return JSON.readTree(response.body());
    }

    private long createProduct(String sku, int stock, int minStock) throws Exception {
        HttpResponse<String> response = call("POST", "/api/products", """
                {"sku":"%s","name":"Product %s","category":"General","price":9.95,"initialStock":%d,"minStock":%d}
                """.formatted(sku, sku, stock, minStock));
        assertEquals(201, response.statusCode());
        return json(response).get("id").asLong();
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        HttpResponse<String> response = call("GET", "/api/health", null);

        assertEquals(200, response.statusCode());
        assertEquals("UP", json(response).get("status").asText());
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        HttpResponse<String> response = call("POST", "/api/products", """
                {"sku":"box-1","name":"Cardboard box","price":1.5,"initialStock":40,"minStock":10}
                """);

        assertEquals(201, response.statusCode());
        JsonNode body = json(response);
        assertEquals("BOX-1", body.get("sku").asText());
        assertEquals(40, body.get("stock").asInt());
        assertEquals(1.5, body.get("price").asDouble());
        assertTrue(body.get("createdAt").asText().endsWith("Z"));
        assertEquals("/api/products/" + body.get("id").asLong(), response.headers().firstValue("Location").orElse(""));
    }

    @Test
    void duplicateSkuReturns409() throws Exception {
        createProduct("DUP", 0, 0);

        HttpResponse<String> response = call("POST", "/api/products", """
                {"sku":"DUP","name":"Other","price":1}
                """);

        assertEquals(409, response.statusCode());
        assertEquals("Conflict", json(response).get("error").asText());
    }

    @Test
    void invalidBodyReturns400WithDetails() throws Exception {
        HttpResponse<String> response = call("POST", "/api/products", """
                {"sku":"","name":"","price":-3}
                """);

        assertEquals(400, response.statusCode());
        assertEquals(3, json(response).get("details").size());
    }

    @Test
    void malformedJsonAndUnknownFieldsReturn400() throws Exception {
        assertEquals(400, call("POST", "/api/products", "{not json").statusCode());
        assertEquals(400, call("POST", "/api/products", """
                {"sku":"A","name":"B","price":1,"colour":"red"}
                """).statusCode());
        assertEquals(400, call("POST", "/api/products", null).statusCode());
    }

    @Test
    void getReturnsProductOr404() throws Exception {
        long id = createProduct("GET-1", 5, 0);

        assertEquals(200, call("GET", "/api/products/" + id, null).statusCode());
        assertEquals(404, call("GET", "/api/products/99999", null).statusCode());
        assertEquals(400, call("GET", "/api/products/abc", null).statusCode());
    }

    @Test
    void unknownRouteIs404AndWrongMethodIs405() throws Exception {
        assertEquals(404, call("GET", "/api/unknown", null).statusCode());
        assertEquals(405, call("PATCH", "/api/products", "{}").statusCode());
    }

    @Test
    void updateChangesTheProduct() throws Exception {
        long id = createProduct("UPD-1", 5, 0);

        HttpResponse<String> response = call("PUT", "/api/products/" + id, """
                {"name":"Renamed","category":"Other","price":12.00,"minStock":3}
                """);

        assertEquals(200, response.statusCode());
        assertEquals("Renamed", json(response).get("name").asText());
        assertEquals(3, json(response).get("minStock").asInt());
    }

    @Test
    void archiveReturns204AndHidesTheProduct() throws Exception {
        long id = createProduct("DEL-1", 0, 0);

        assertEquals(204, call("DELETE", "/api/products/" + id, null).statusCode());
        assertEquals(404, call("GET", "/api/products/" + id, null).statusCode());
        assertEquals(404, call("DELETE", "/api/products/" + id, null).statusCode());
    }

    @Test
    void stockFlowThroughTheApi() throws Exception {
        long id = createProduct("FLOW-1", 10, 5);

        HttpResponse<String> exit = call("POST", "/api/products/" + id + "/movements", """
                {"type":"EXIT","quantity":6,"reason":"Order 15"}
                """);
        assertEquals(201, exit.statusCode());
        assertEquals(4, json(exit).get("stockAfter").asInt());

        HttpResponse<String> tooMuch = call("POST", "/api/products/" + id + "/movements", """
                {"type":"EXIT","quantity":5}
                """);
        assertEquals(409, tooMuch.statusCode());

        HttpResponse<String> badType = call("POST", "/api/products/" + id + "/movements", """
                {"type":"STEAL","quantity":1}
                """);
        assertEquals(400, badType.statusCode());

        JsonNode product = json(call("GET", "/api/products/" + id, null));
        assertEquals(4, product.get("stock").asInt());
        assertTrue(product.get("lowStock").asBoolean());

        JsonNode history = json(call("GET", "/api/products/" + id + "/movements", null));
        assertEquals(2, history.size());
        assertEquals("EXIT", history.get(0).get("type").asText());
        assertEquals("Order 15", history.get(0).get("reason").asText());

        assertEquals(2, json(call("GET", "/api/movements?limit=10", null)).size());
    }

    @Test
    void listSupportsFiltersAndRejectsBadParameters() throws Exception {
        createProduct("LIST-1", 100, 10);
        createProduct("LIST-2", 2, 10);

        assertEquals(2, json(call("GET", "/api/products", null)).size());
        JsonNode low = json(call("GET", "/api/products?lowStock=true", null));
        assertEquals(1, low.size());
        assertEquals("LIST-2", low.get(0).get("sku").asText());
        assertEquals(1, json(call("GET", "/api/products?q=list-1", null)).size());

        assertEquals(400, call("GET", "/api/products?limit=abc", null).statusCode());
        assertEquals(400, call("GET", "/api/products?lowStock=maybe", null).statusCode());
        assertEquals(400, call("GET", "/api/products?limit=0", null).statusCode());
    }

    @Test
    void errorResponsesNeverLeakInternals() throws Exception {
        HttpResponse<String> response = call("GET", "/api/products/99999", null);

        assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(!response.body().contains("Exception"));
    }
}
