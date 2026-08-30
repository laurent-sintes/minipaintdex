package com.minipaintdex.server.api;

import com.minipaintdex.adapter.file.FileStorageException;
import com.minipaintdex.domain.workflow.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(DomainException.class)
    ResponseEntity<Map<String, Object>> domain(DomainException exception) {
        var status = switch (exception.code()) {
            case "not_found" -> HttpStatus.NOT_FOUND;
            case "conflict", "invalid_transition" -> HttpStatus.CONFLICT;
            case "invalid_input" -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(error(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        return ResponseEntity.unprocessableContent().body(error("invalid_input", "Request validation failed."));
    }

    @ExceptionHandler(FileStorageException.class)
    ResponseEntity<Map<String, Object>> storage(FileStorageException exception) {
        return ResponseEntity.internalServerError().body(error("storage_failure", exception.getMessage()));
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of("error", Map.of("code", code, "message", message));
    }
}
