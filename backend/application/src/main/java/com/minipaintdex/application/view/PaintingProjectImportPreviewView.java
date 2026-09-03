package com.minipaintdex.application.view;

import java.util.List;

/** Non-mutating impact analysis performed before importing a market product into the workshop. */
public record PaintingProjectImportPreviewView(
        String paintableProductId,
        String paintableProductName,
        int paintableComponentCount,
        int paintableCount,
        int paintingGuideCount,
        int requiredPaintCount,
        int missingPaintCount,
        List<MissingPaintView> missingPaints,
        int pendingPaintSlotCount,
        boolean alreadyImported) {
    public PaintingProjectImportPreviewView {
        missingPaints = List.copyOf(missingPaints);
    }
}
