package com.minipaintdex.application.result;

public record ApplyMarketPaintableProductChangeSetResult(
        String productId,
        int catalogItems,
        int paintingGuides,
        boolean applied) {
}
