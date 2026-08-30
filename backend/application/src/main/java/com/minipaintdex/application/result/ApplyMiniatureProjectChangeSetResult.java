package com.minipaintdex.application.result;

public record ApplyMiniatureProjectChangeSetResult(
        String projectId,
        int catalogItems,
        int paintingGuides,
        int workshopItemsAdded,
        int workshopItemsExisting,
        boolean applied) {
}
