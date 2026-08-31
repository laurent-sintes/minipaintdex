package com.minipaintdex.application.result;

import com.minipaintdex.application.event.PublicationReceipt;

public record CreatePaintingProjectResult(
        String workshopId,
        String paintingProjectId,
        String paintableProductId,
        int workshopItemsAdded,
        int workshopItemsExisting,
        boolean alreadyExists,
        boolean applied,
        PublicationReceipt publication) {
}
