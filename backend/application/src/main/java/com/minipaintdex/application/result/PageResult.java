package com.minipaintdex.application.result;

import java.util.List;

/** Immutable application result for a bounded page. */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {
    public PageResult {
        content = List.copyOf(content);
        if (page < 0 || size < 1 || totalElements < 0) throw new IllegalArgumentException("Invalid page metadata");
    }

    public int totalPages() {
        return totalElements == 0 ? 0 : Math.toIntExact((totalElements + size - 1) / size);
    }

    public boolean hasPrevious() { return page > 0; }
    public boolean hasNext() { return (long) (page + 1) * size < totalElements; }
}
