package com.minipaintdex.application.query;

import com.minipaintdex.domain.shared.DomainException;

import java.util.List;

/** Framework-independent page and sort request. */
public record PageQuery(int page, int size, List<SortOrder> sort) {
    public static final int MAX_SIZE = 200;

    public PageQuery {
        sort = sort == null ? List.of() : List.copyOf(sort);
        if (page < 0) throw new DomainException("invalid_input", "page cannot be negative.");
        if (size < 1 || size > MAX_SIZE) {
            throw new DomainException("invalid_input", "size must be between 1 and " + MAX_SIZE + ".");
        }
    }

    public int offset() {
        return Math.multiplyExact(page, size);
    }
}
