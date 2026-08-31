package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.shared.DomainException;

import java.time.Instant;
import java.util.Objects;

final class DomainFields {
    private DomainFields() {}

    static String required(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " is required.");
        return value;
    }

    static Instant required(Instant value, String field) {
        return Objects.requireNonNull(value, field + " is required.");
    }

    static String optional(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static DomainException invalid(String message) {
        return new DomainException("invalid_domain_event", message);
    }
}
