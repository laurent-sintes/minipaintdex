package com.minipaintdex.application.view;

import java.util.List;

/** Sourced professional or community paint knowledge for one market catalog item. */
public record MarketPaintingGuideView(
        String id,
        String catalogItemId,
        int version,
        String knowledgeStatus,
        List<PaintableProductView.SourceView> sources,
        List<SlotView> slots,
        List<PaintableProductView.GuideStepView> preparation,
        List<PaintableProductView.GuideStepView> painting) {
    public MarketPaintingGuideView {
        sources = List.copyOf(sources);
        slots = List.copyOf(slots);
        preparation = List.copyOf(preparation);
        painting = List.copyOf(painting);
    }

    public record SlotView(
            String id,
            String role,
            String marketPaintId,
            boolean pendingImport,
            RequestedPaintView requestedPaint) {}

    public record RequestedPaintView(String brand, String name, String colorHex) {}
}
