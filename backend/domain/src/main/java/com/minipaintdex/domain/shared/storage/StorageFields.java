package com.minipaintdex.domain.shared.storage;

import com.minipaintdex.domain.shared.DomainException;
import java.util.List;

public final class StorageFields {
    private StorageFields() {}
    public static String text(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " is required.");
        return value.trim();
    }
    public static String id(String value) {
        if (!text(value, "id").matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw invalid("Invalid stable identifier: " + value);
        return value;
    }
    public static Double dimension(Double value) {
        if (value != null && (!Double.isFinite(value) || value <= 0)) throw invalid("Dimensions must be finite and positive.");
        return value;
    }
    public static String evidence(String value) {
        if (!List.of("confirmed", "estimated", "unknown").contains(value)) throw invalid("Invalid evidence status.");
        return value;
    }
    public static List<String> sources(List<String> values) {
        var result = values == null ? List.<String>of() : List.copyOf(values);
        result.forEach(value -> {
            var uri = java.net.URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null) throw invalid("Sources must be HTTPS URLs.");
        });
        return result;
    }
    public static DomainException invalid(String message) { return new DomainException("invalid_input", message); }
}
