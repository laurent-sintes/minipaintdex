package com.minipaintdex.application.port;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.domain.market.product.PaintableProduct;

import java.util.List;

/** Immutable reference data owned and published by the Market bounded context. */
public record MarketCatalogSnapshot(
        List<StructuredDocument> paints,
        List<PaintableProduct> paintableProducts,
        List<StructuredDocument> paintingGuides) {
    public MarketCatalogSnapshot {
        paints = List.copyOf(paints);
        paintableProducts = List.copyOf(paintableProducts);
        paintingGuides = List.copyOf(paintingGuides);
    }
}
