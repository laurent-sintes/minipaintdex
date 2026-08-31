package com.minipaintdex.application.view;

import java.util.List;

/** Detailed market knowledge for one product containing miniatures or scenery to paint. */
public record PaintableProductView(
        int schemaVersion,
        String id,
        String name,
        String line,
        String productType,
        String scope,
        int expectedPaintableCount,
        EditionView edition,
        List<SourceView> sources,
        List<CatalogItemView> items) {
    public PaintableProductView {
        sources = List.copyOf(sources);
        items = List.copyOf(items);
    }

    public record EditionView(String note, String url) {}
    public record SourceView(String kind, String label, String url) {}
    public record ReferenceImageView(String url, String pageUrl, String credit, String license) {}
    public record GuidePaintView(
            String slotId, String paintId, String brand, String name, String role,
            String colorHex, boolean pendingImport) {}
    public record GuideStepView(String title, String detail) {}
    public record MarketGuideView(
            String id, int version, String knowledgeStatus, List<SourceView> sources) {
        public MarketGuideView { sources = List.copyOf(sources); }
    }
    public record CatalogItemView(
            String id,
            String productId,
            String name,
            String kind,
            int quantity,
            String description,
            boolean assemblyRequired,
            List<ReferenceImageView> referenceImages,
            List<GuidePaintView> paints,
            List<GuideStepView> preparation,
            List<GuideStepView> painting,
            MarketGuideView marketGuide,
            List<SourceView> sources) {
        public CatalogItemView {
            referenceImages = List.copyOf(referenceImages);
            paints = List.copyOf(paints);
            preparation = List.copyOf(preparation);
            painting = List.copyOf(painting);
            sources = List.copyOf(sources);
        }
    }
}
