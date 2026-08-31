package com.minipaintdex.application.document;

import java.util.List;
import java.util.HashSet;
import java.util.Objects;

/**
 * Immutable, serialization-neutral document used only at versioned bulk-import boundaries.
 * Domain commands validate and translate its schema before any persistence mutation.
 */
public record StructuredDocument(List<Field> fields) {
    public StructuredDocument {
        fields = fields == null ? List.of() : List.copyOf(fields);
        var names = new HashSet<String>();
        for (var field : fields) {
            Objects.requireNonNull(field, "Document fields cannot contain null entries.");
            if (!names.add(field.name())) {
                throw new IllegalArgumentException("Duplicate document field: " + field.name());
            }
        }
    }

    public sealed interface Value permits Text, NumberValue, BooleanValue, NullValue, ArrayValue, ObjectValue {}
    public record Text(String value) implements Value {
        public Text { Objects.requireNonNull(value, "Text value is required."); }
    }
    public record NumberValue(Number value) implements Value {
        public NumberValue { Objects.requireNonNull(value, "Number value is required."); }
    }
    public record BooleanValue(boolean value) implements Value {}
    public record NullValue() implements Value {}
    public record ArrayValue(List<Value> values) implements Value {
        public ArrayValue {
            values = values == null ? List.of() : List.copyOf(values);
            if (values.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Array values cannot contain null; use NullValue.");
            }
        }
    }
    public record ObjectValue(StructuredDocument value) implements Value {
        public ObjectValue { Objects.requireNonNull(value, "Object value is required."); }
    }
    public record Field(String name, Value value) {
        public Field {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Field name is required.");
            value = Objects.requireNonNull(value, "Field value is required; use NullValue.");
        }
    }
}
