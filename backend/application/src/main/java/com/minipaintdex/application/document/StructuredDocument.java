package com.minipaintdex.application.document;

import java.util.List;

/**
 * Immutable, serialization-neutral document used only at versioned bulk-import boundaries.
 * Domain commands validate and translate its schema before any persistence mutation.
 */
public record StructuredDocument(List<Field> fields) {
    public StructuredDocument { fields = fields == null ? List.of() : List.copyOf(fields); }

    public sealed interface Value permits Text, NumberValue, BooleanValue, NullValue, ArrayValue, ObjectValue {}
    public record Text(String value) implements Value {}
    public record NumberValue(Number value) implements Value {}
    public record BooleanValue(boolean value) implements Value {}
    public record NullValue() implements Value {}
    public record ArrayValue(List<Value> values) implements Value {
        public ArrayValue { values = values == null ? List.of() : List.copyOf(values); }
    }
    public record ObjectValue(StructuredDocument value) implements Value {}
    public record Field(String name, Value value) {}
}
