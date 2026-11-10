package com.warehouse.inventory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.warehouse.inventory.exception.ApiException;
import com.warehouse.inventory.exception.ValidationException;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(int status, String error, String message, List<String> details) {

    public static ApiError from(ApiException exception) {
        List<String> details = exception instanceof ValidationException validation
                ? validation.details()
                : List.of();
        return new ApiError(exception.status(), reason(exception.status()), exception.getMessage(), details);
    }

    public static ApiError internal() {
        return new ApiError(500, reason(500), "Unexpected server error", List.of());
    }

    private static String reason(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 413 -> "Payload Too Large";
            default -> "Internal Server Error";
        };
    }
}
