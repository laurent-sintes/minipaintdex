package com.minipaintdex.application.port;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.market.product.PaintableProduct;

import java.util.List;

public record DataSnapshot(
        StructuredDocument site,
        List<StructuredDocument> marketPaints,
        List<StructuredDocument> paintInventory,
        List<PaintableProduct> paintableProducts,
        List<StructuredDocument> marketPaintingGuides,
        List<StructuredDocument> shopping,
        List<EventEnvelope> events) {
}
