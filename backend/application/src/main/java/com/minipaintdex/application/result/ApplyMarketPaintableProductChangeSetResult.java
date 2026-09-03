package com.minipaintdex.application.result;

public record ApplyMarketPaintableProductChangeSetResult(
        String paintableProductId,
        int paintableComponents,
        int paintingGuides,
        boolean applied) {
}
