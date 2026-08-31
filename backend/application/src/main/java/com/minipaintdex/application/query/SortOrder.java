package com.minipaintdex.application.query;

/** One application-level sort criterion, independent from Spring Data. */
public record SortOrder(String property, Direction direction) {
    public SortOrder {
        if (property == null || property.isBlank()) throw new IllegalArgumentException("property is required");
        if (direction == null) direction = Direction.ASCENDING;
    }

    public enum Direction { ASCENDING, DESCENDING }
}
