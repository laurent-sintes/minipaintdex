package com.minipaintdex.application.port;

import com.minipaintdex.domain.market.guide.MarketPaintingGuide;
import com.minipaintdex.domain.market.paint.PaintProduct;
import com.minipaintdex.domain.market.paint.PaintCatalogEdition;
import com.minipaintdex.domain.market.product.PaintableProduct;

import java.util.List;

/** Immutable reference data owned and published by the Market bounded context. */
public record MarketCatalogSnapshot(
        List<PaintProduct> paints,
        List<PaintableProduct> paintableProducts,
        List<MarketPaintingGuide> paintingGuides,
        List<PaintCatalogEdition> paintCatalogEditions,
        List<com.minipaintdex.domain.market.paint.PaintUsageGuide> paintUsageGuides) {
    public MarketCatalogSnapshot {
        paints = List.copyOf(paints);
        paintableProducts = List.copyOf(paintableProducts);
        paintingGuides = List.copyOf(paintingGuides);
        paintCatalogEditions = List.copyOf(paintCatalogEditions);
        paintUsageGuides = List.copyOf(paintUsageGuides);
    }
}
