package com.minipaintdex.application.view;

/** Lightweight market product read model used by collection pages and workshop projections. */
public record PaintableProductSummaryView(
        String id,
        String name,
        String line,
        String productType,
        String scope,
        int catalogItemCount,
        int expectedPaintableCount) {}
