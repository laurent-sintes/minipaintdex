package com.minipaintdex.application.view;

import java.util.List;

/**
 * Immutable localized UI configuration tree.
 *
 * <p>The application keeps configuration values typed without depending on YAML, Jackson or a
 * transport map. The REST adapter alone decides how entries become a JSON object.</p>
 */
public record SiteConfigurationView(Section root) {
    public sealed interface Value permits TextValue, NumberValue, BooleanValue, ListValue, Section {}
    public record TextValue(String value) implements Value {}
    public record NumberValue(Number value) implements Value {}
    public record BooleanValue(boolean value) implements Value {}
    public record ListValue(List<Value> values) implements Value {
        public ListValue { values = List.copyOf(values); }
    }
    public record Section(List<Entry> entries) implements Value {
        public Section { entries = List.copyOf(entries); }
    }
    public record Entry(String key, Value value) {}
}
