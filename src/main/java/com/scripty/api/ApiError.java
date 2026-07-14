package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * The single error body shape for every non-2xx /api response, so native
 * clients (SwiftUI) can decode all failures with one Decodable: a stable
 * machine-readable {@code error} code, a human-readable {@code message}, and
 * per-field messages for validation failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiError {

    private final String error;
    private final String message;
    private final Map<String, String> fieldErrors;

    private ApiError(String error, String message, Map<String, String> fieldErrors) {
        this.error = error;
        this.message = message;
        this.fieldErrors = fieldErrors;
    }

    public static ApiError of(String error, String message) {
        return new ApiError(error, message, null);
    }

    public static ApiError validation(Map<String, String> fieldErrors) {
        return new ApiError("validation_failed", "One or more fields are invalid.", fieldErrors);
    }

    public static ApiError forbidden() {
        return of("forbidden", "You do not have access to this resource.");
    }

    public static ApiError notFound() {
        return of("not_found", "The requested resource does not exist.");
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
