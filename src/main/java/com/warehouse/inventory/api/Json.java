package com.warehouse.inventory.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.warehouse.inventory.exception.ValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .registerModule(new SimpleModule().addSerializer(Instant.class, new ToStringSerializer(Instant.class)));

    private Json() {
    }

    public static <T> T read(byte[] body, Class<T> type) {
        if (body == null || body.length == 0) {
            throw new ValidationException("Request body is required");
        }
        try {
            return MAPPER.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Malformed or invalid JSON body");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] write(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise response", e);
        }
    }
}
