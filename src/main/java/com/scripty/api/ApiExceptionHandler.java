package com.scripty.api;

import com.scripty.controller.ActorRestController;
import com.scripty.controller.BlockRestController;
import com.scripty.controller.PersonRestController;
import com.scripty.controller.ProjectRestController;
import com.scripty.controller.TeamRestController;
import com.scripty.controller.UserRestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Framework-level request errors (unparseable JSON, missing or mistyped
 * parameters) otherwise fall through to Spring Boot's default error body,
 * whose shape differs from the API's {@link ApiError} envelope — and which
 * includes a stack trace in dev. Native clients get the envelope everywhere.
 */
@RestControllerAdvice(assignableTypes = {
        ApiRootController.class,
        ProjectRestController.class,
        BlockRestController.class,
        PersonRestController.class,
        ActorRestController.class,
        TeamRestController.class,
        UserRestController.class})
public class ApiExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError malformedBody(HttpMessageNotReadableException ex) {
        return ApiError.of("malformed_request", "The request body is not valid JSON.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError missingParameter(MissingServletRequestParameterException ex) {
        return ApiError.of("missing_parameter",
                "Required parameter '" + ex.getParameterName() + "' is missing.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidParameter(MethodArgumentTypeMismatchException ex) {
        return ApiError.of("invalid_parameter",
                "Parameter '" + ex.getName() + "' has an invalid value.");
    }
}
