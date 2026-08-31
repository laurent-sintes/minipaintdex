package com.minipaintdex.domain.market;

import com.minipaintdex.domain.market.guide.MarketPaintingGuide;
import com.minipaintdex.domain.market.paint.MarketPaint;
import com.minipaintdex.domain.market.paint.MarketPaintLifecycle;
import com.minipaintdex.domain.market.paint.MarketPaintType;
import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketCatalogAggregateTest {
    @Test
    void requiresInstructionsForBehavioralPaintTypes() {
        var failure = assertThrows(DomainException.class, () -> paint(
                MarketPaintType.TECHNICAL_EFFECT, MarketPaint.UsageInstructions.empty()));

        assertEquals("invalid_market_paint", failure.code());
    }

    @Test
    void normalizesColorsAndDefensivelyCopiesMetadata() {
        var tags = new java.util.ArrayList<>(List.of("cold"));
        var paint = paint(MarketPaintType.OPAQUE_STANDARD, MarketPaint.UsageInstructions.empty(), tags);
        tags.add("mutated");

        assertEquals("#aabbcc", paint.color().hex());
        assertEquals(List.of("cold"), paint.tags());
    }

    @Test
    void rejectsGuideWithoutKnowledgeSource() {
        assertThrows(DomainException.class, () -> guide(List.of(), List.of()));
    }

    @Test
    void rejectsDuplicateGuideSlots() {
        var slot = new MarketPaintingGuide.Slot(
                "base", "Base coat", "brand-range-paint", false,
                MarketPaintingGuide.RequestedPaint.empty());

        assertThrows(DomainException.class, () -> guide(List.of("source-1"), List.of(slot, slot)));
    }

    private static MarketPaint paint(
            MarketPaintType type, MarketPaint.UsageInstructions instructions) {
        return paint(type, instructions, List.of());
    }

    private static MarketPaint paint(
            MarketPaintType type, MarketPaint.UsageInstructions instructions, List<String> tags) {
        return new MarketPaint(
                1, "brand-range-paint", "Brand", "Maker", List.of(), "Range", type,
                "001", "Paint", new MarketPaint.Color("Blue", "#AABBCC"), "matt", "acrylic",
                "opaque", MarketPaintLifecycle.ACTIVE, "verified", List.of(), tags, List.of(), null,
                null, MarketPaint.ImageReference.empty(), 18, List.of(), instructions, null,
                MarketPaint.ImageReference.empty());
    }

    private static MarketPaintingGuide guide(
            List<String> sourceReferences, List<MarketPaintingGuide.Slot> slots) {
        return new MarketPaintingGuide(
                1, "guide", 1, MarketPaintingGuide.KnowledgeStatus.DOCUMENTED, "product-item",
                sourceReferences, new MarketPaintingGuide.Provenance("test", false), List.of(), slots,
                List.of(), List.of());
    }
}
