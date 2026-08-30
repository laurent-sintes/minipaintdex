package com.minipaintdex.application.port;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.product.PaintableProduct;

import java.util.List;
import java.util.Map;

public record DataSnapshot(
        Map<String, Object> site,
        List<Map<String, Object>> marketPaints,
        List<Map<String, Object>> paintInventory,
        List<PaintableProduct> paintableProducts,
        List<Map<String, Object>> marketPaintingGuides,
        List<Map<String, Object>> shopping,
        List<DomainEvent> events) {
}
