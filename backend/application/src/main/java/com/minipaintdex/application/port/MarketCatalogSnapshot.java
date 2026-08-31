package com.minipaintdex.application.port;

import com.minipaintdex.domain.market.guide.MarketPaintingGuide;
import com.minipaintdex.domain.market.paint.MarketPaint;
import com.minipaintdex.domain.market.product.PaintableProduct;

import java.util.List;

/** Immutable reference data owned and published by the Market bounded context. */
public record MarketCatalogSnapshot(
        List<MarketPaint> paints,
        List<PaintableProduct> paintableProducts,
        List<MarketPaintingGuide> paintingGuides) {
    public MarketCatalogSnapshot {
        paints = List.copyOf(paints);
        paintableProducts = List.copyOf(paintableProducts);
        paintingGuides = List.copyOf(paintingGuides);
    }
}
