package com.minipaintdex.application.validation;

import com.minipaintdex.application.document.StructuredDocument;

import com.minipaintdex.domain.shared.DomainException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic conversion utilities reserved for versioned structured import documents. */
public final class StructuredDocuments {
    private StructuredDocuments() {}

    public static Map<String, Object> toMap(StructuredDocument document) {
        if (document == null) throw invalid("Document is required.");
        var result = new LinkedHashMap<String, Object>();
        document.fields().forEach(field -> result.put(field.name(), toValue(field.value())));
        return Collections.unmodifiableMap(result);
    }

    public static List<Map<String, Object>> toMaps(List<StructuredDocument> documents) {
        if (documents == null) return List.of();
        return documents.stream().map(StructuredDocuments::toMap).toList();
    }

    public static StructuredDocument fromMap(Map<String, Object> document) {
        if (document == null) throw invalid("Document map is required.");
        return new StructuredDocument(document.entrySet().stream()
                .map(entry -> new StructuredDocument.Field(entry.getKey(), fromValue(entry.getValue())))
                .toList());
    }

    public static List<StructuredDocument> fromMaps(List<Map<String, Object>> documents) {
        if (documents == null) return List.of();
        return documents.stream().map(StructuredDocuments::fromMap).toList();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    public static List<Map<String, Object>> maps(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list) || list.stream().anyMatch(entry -> !(entry instanceof Map<?, ?>))) {
            throw invalid("Expected a list of objects.");
        }
        return list.stream().map(StructuredDocuments::map).toList();
    }

    public static List<String> strings(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw invalid("Expected a list of strings.");
        if (list.stream().anyMatch(entry -> entry == null || entry instanceof Map<?, ?> || entry instanceof List<?>)) {
            throw invalid("Expected a list of scalar strings.");
        }
        return list.stream().map(String::valueOf).toList();
    }

    public static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static int integer(Object value, String field) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException exception) {
            throw new DomainException("invalid_input", field + " must be an integer.");
        }
    }

    private static Object toValue(StructuredDocument.Value value) {
        return switch (value) {
            case StructuredDocument.Text text -> text.value();
            case StructuredDocument.NumberValue number -> number.value();
            case StructuredDocument.BooleanValue bool -> bool.value();
            case StructuredDocument.NullValue ignored -> null;
            case StructuredDocument.ArrayValue array -> array.values().stream()
                    .map(StructuredDocuments::toValue).toList();
            case StructuredDocument.ObjectValue object -> toMap(object.value());
        };
    }

    private static StructuredDocument.Value fromValue(Object value) {
        if (value == null) return new StructuredDocument.NullValue();
        if (value instanceof Map<?, ?> nested) return new StructuredDocument.ObjectValue(fromMap(map(nested)));
        if (value instanceof List<?> list) {
            return new StructuredDocument.ArrayValue(list.stream().map(StructuredDocuments::fromValue).toList());
        }
        if (value instanceof Number number) return new StructuredDocument.NumberValue(number);
        if (value instanceof Boolean bool) return new StructuredDocument.BooleanValue(bool);
        return new StructuredDocument.Text(String.valueOf(value));
    }

    private static DomainException invalid(String message) {
        return new DomainException("invalid_input", message);
    }
}
