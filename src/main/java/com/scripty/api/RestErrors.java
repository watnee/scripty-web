package com.scripty.api;

import java.util.HashMap;
import java.util.Map;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

public final class RestErrors {

    private RestErrors() {
    }

    /**
     * A one-field error body in the same shape validation produces, for the
     * failures that never reach a {@link BindingResult} — a rejected upload,
     * say. A client that already reads the validation shape needs no second
     * one.
     */
    public static Map<String, String> of(String field, String message) {
        Map<String, String> errors = new HashMap<>();
        errors.put(field, message);
        return errors;
    }

    public static Map<String, String> from(BindingResult bindingResult) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : bindingResult.getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return errors;
    }
}
