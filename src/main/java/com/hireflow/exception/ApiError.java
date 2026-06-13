package com.hireflow.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> violations) {

    public record FieldViolation(String field, String message) { }

    public static ApiError of(HttpStatus status, String error, String message, String path) {
        return new ApiError(Instant.now(), status.value(), error, message, path, null);
    }

    public static ApiError of(HttpStatus status, String error, String message, String path,
                              List<FieldViolation> violations) {
        return new ApiError(Instant.now(), status.value(), error, message, path, violations);
    }
}
