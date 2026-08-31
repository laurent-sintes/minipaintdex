package com.minipaintdex.application.result;

public record CreatePaintingProjectResult(
        String workshopId,
        String paintingProjectId,
        String paintableProductId,
        int workshopItemsAdded,
        int workshopItemsExisting,
        boolean alreadyExists,
        boolean applied) {
}
