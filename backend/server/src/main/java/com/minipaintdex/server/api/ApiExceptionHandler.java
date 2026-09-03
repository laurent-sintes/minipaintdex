package com.minipaintdex.server.api;

import com.minipaintdex.application.port.PersistenceException;
import com.minipaintdex.domain.shared.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> domain(DomainException exception) {
        var status = switch (exception.code()) {
            case "not_found" -> HttpStatus.NOT_FOUND;
            case "conflict", "invalid_transition" -> HttpStatus.CONFLICT;
            case "invalid_input" -> HttpStatus.UNPROCESSABLE_CONTENT;
            case "search_unavailable", "photo_processing_unavailable" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(problem(status, exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadableJson() {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "invalid_json",
                "Malformed JSON or unsupported request fields. Check the API contract."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception) {
        return ResponseEntity.unprocessableContent().body(problem(
                HttpStatus.UNPROCESSABLE_CONTENT, "invalid_input", "Request validation failed."));
    }

    @ExceptionHandler(PersistenceException.class)
    ResponseEntity<ProblemDetail> storage(PersistenceException exception) {
        return ResponseEntity.internalServerError().body(problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "storage_failure", exception.getMessage()));
    }

    private ProblemDetail problem(HttpStatus status, String code, String message) {
        var problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setType(URI.create("urn:minipaintdex:problem:" + code));
        problem.setTitle(code.replace('_', ' '));
        problem.setProperty("code", code);
        return problem;
    }
}
