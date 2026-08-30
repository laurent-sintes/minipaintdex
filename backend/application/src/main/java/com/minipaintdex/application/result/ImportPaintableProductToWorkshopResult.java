package com.minipaintdex.application.result;

public record ImportPaintableProductToWorkshopResult(
        String workshopId,
        String productId,
        int workshopItemsAdded,
        int workshopItemsExisting,
        boolean alreadyImported,
        boolean applied) {
}
