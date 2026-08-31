package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.shared.DomainException;

import java.time.Instant;
import java.util.Objects;

final class DomainFields {
    private static final String STABLE_ID = "[a-z0-9]+(?:-[a-z0-9]+)*";

    private DomainFields() {}

    static String required(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " is required.");
        return value;
    }

    static String id(String value, String field) {
        var result = required(value, field);
        if (!result.matches(STABLE_ID)) {
            throw invalid(field + " must be a lowercase ASCII kebab-case identifier.");
        }
        return result;
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
