package com.minipaintdex.application.view;

import java.util.List;

/** Non-mutating impact analysis performed before importing a market product into the workshop. */
public record ProductImportPreviewView(
        String productId,
        String productName,
        int catalogItemCount,
        int paintableItemCount,
        int paintingGuideCount,
        int requiredPaintCount,
        int missingPaintCount,
        List<MissingPaintView> missingPaints,
        int pendingPaintSlotCount,
        boolean alreadyImported) {
    public ProductImportPreviewView {
        missingPaints = List.copyOf(missingPaints);
    }
}
