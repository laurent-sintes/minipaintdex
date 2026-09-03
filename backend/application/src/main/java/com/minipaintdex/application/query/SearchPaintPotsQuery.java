package com.minipaintdex.application.query;

public record SearchPaintPotsQuery(String paintProductId, boolean includeRemoved, PageQuery page) {
    public SearchPaintPotsQuery { java.util.Objects.requireNonNull(page, "page is required"); }
}
