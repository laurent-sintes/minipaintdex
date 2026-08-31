package com.minipaintdex.application.port;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.market.product.PaintableProduct;

import java.util.List;

/**
 * Immutable persistence generation used by administration and projection code.
 *
 * <p>The structured documents are deliberately confined to versioned file/import boundaries;
 * normal Market queries translate them into typed domain objects through {@code MarketCatalogFactory}.</p>
 */
public record DataSnapshot(
        StructuredDocument site,
        List<StructuredDocument> marketPaints,
        List<StructuredDocument> paintInventory,
        List<PaintableProduct> paintableProducts,
        List<StructuredDocument> marketPaintingGuides,
        List<StructuredDocument> shopping,
        List<EventEnvelope> events) {
    public DataSnapshot {
        if (site == null) throw new IllegalArgumentException("site is required.");
        marketPaints = copy(marketPaints);
        paintInventory = copy(paintInventory);
        paintableProducts = copy(paintableProducts);
        marketPaintingGuides = copy(marketPaintingGuides);
        shopping = copy(shopping);
        events = copy(events);
    }

    private static <T> List<T> copy(List<T> values) {
        if (values == null) return List.of();
        if (values.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Snapshot collections cannot contain null entries.");
        }
        return List.copyOf(values);
    }
}
