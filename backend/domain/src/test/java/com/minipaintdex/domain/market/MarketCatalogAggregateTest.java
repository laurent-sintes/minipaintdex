package com.minipaintdex.domain.market;

import com.minipaintdex.domain.market.guide.MarketPaintingGuide;
import com.minipaintdex.domain.market.paint.PaintProduct;
import com.minipaintdex.domain.market.paint.PaintProductLifecycle;
import com.minipaintdex.domain.market.paint.PaintProductProfile;
import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.net.URI;
import java.time.LocalDate;
import com.minipaintdex.domain.market.paint.PaintProductImageQuality;
import com.minipaintdex.domain.market.paint.PaintProductImageLimitationCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketCatalogAggregateTest {
    @Test
    void requiresInstructionsForBehavioralPaintTypes() {
        var failure = assertThrows(DomainException.class, () -> paint(
                PaintProductProfile.Role.TECHNICAL_EFFECT, PaintProduct.UsageInstructions.empty()));

        assertEquals("invalid_market_paint", failure.code());
    }

    @Test
    void normalizesColorsAndDefensivelyCopiesMetadata() {
        var tags = new java.util.ArrayList<>(List.of("cold"));
        var paint = paint(PaintProductProfile.Role.COLOR_PAINT, PaintProduct.UsageInstructions.empty(), tags);
        tags.add("mutated");

        assertEquals("#aabbcc", paint.color().hex());
        assertEquals(List.of("cold"), paint.tags());
    }

    @Test
    void rejectsGuideWithoutKnowledgeSource() {
        assertThrows(DomainException.class, () -> guide(List.of(), List.of()));
    }

    @Test
    void rejectsAnyPaintSchemaOtherThanOne() {
        assertThrows(DomainException.class, () -> paint(
                2, PaintProductProfile.Role.COLOR_PAINT, PaintProduct.UsageInstructions.empty(), List.of()));
    }

    @Test
    void rejectsDuplicateGuideSlots() {
        var slot = new MarketPaintingGuide.Slot(
                "base", "Base coat", "brand-range-paint", false,
                MarketPaintingGuide.RequestedPaint.empty());

        assertThrows(DomainException.class, () -> guide(List.of("source-1"), List.of(slot, slot)));
    }

    @Test
    void rejectsRetailerPhotosWithoutTraceableCreditAndProductPage() {
        assertThrows(DomainException.class, () -> new PaintProduct.ImageReference(
                null, URI.create("https://retailer.test/paint.webp"), null, null, null,
                PaintProductImageQuality.RETAILER_PHOTO, LocalDate.parse("2026-09-01"),
                limitation()));
    }

    @Test
    void requiresALimitationForANonOfficialManufacturerImage() {
        assertThrows(DomainException.class, () -> paintWithManufacturerImage(PaintProduct.ImageReference.empty()));
    }

    @Test
    void rejectsALimitationOnAnOfficialPhoto() {
        assertThrows(DomainException.class, () -> new PaintProduct.ImageReference(
                "/media/official.webp", URI.create("https://maker.test/official.webp"),
                "Official Maker catalogue", null, URI.create("https://maker.test/paint"),
                PaintProductImageQuality.OFFICIAL_PHOTO, LocalDate.parse("2026-09-01"), limitation()));
    }

    private static PaintProduct paint(
            PaintProductProfile.Role role, PaintProduct.UsageInstructions instructions) {
        return paint(role, instructions, List.of());
    }

    private static PaintProduct paint(
            PaintProductProfile.Role role, PaintProduct.UsageInstructions instructions, List<String> tags) {
        return paint(1, role, instructions, tags);
    }

    private static PaintProduct paint(
            int schemaVersion, PaintProductProfile.Role role,
            PaintProduct.UsageInstructions instructions, List<String> tags) {
        return new PaintProduct(
                schemaVersion, "brand-range-paint", "Brand", "Maker", List.of(), "Range",
                new PaintProductProfile(
                        List.of(role), List.of(PaintProductProfile.ApplicationMethod.BRUSH),
                        PaintProductProfile.ApplicationSystem.CONVENTIONAL_LAYERING,
                        PaintProductProfile.Coverage.OPAQUE, PaintProductProfile.Finish.MATTE, List.of(),
                        new PaintProductProfile.Undercoat(PaintProductProfile.UndercoatTone.ANY, false),
                        PaintProductProfile.Medium.WATER_BASED_ACRYLIC),
                "001", "Paint", new PaintProduct.Color("Blue", "#AABBCC"),
                PaintProductLifecycle.ACTIVE, "verified", List.of(), tags, null,
                null, new PaintProduct.ImageReference(
                        null, null, null, null, null, PaintProductImageQuality.NONE, null, limitation()),
                18, List.of(), instructions, null,
                PaintProduct.ImageReference.empty(), List.of(), java.util.List.of(), "test-container");
    }

    private static PaintProduct paintWithManufacturerImage(PaintProduct.ImageReference image) {
        return new PaintProduct(
                1, "brand-range-paint", "Brand", "Maker", List.of(), "Range",
                new PaintProductProfile(
                        List.of(PaintProductProfile.Role.COLOR_PAINT),
                        List.of(PaintProductProfile.ApplicationMethod.BRUSH),
                        PaintProductProfile.ApplicationSystem.CONVENTIONAL_LAYERING,
                        PaintProductProfile.Coverage.OPAQUE, PaintProductProfile.Finish.MATTE, List.of(),
                        new PaintProductProfile.Undercoat(PaintProductProfile.UndercoatTone.ANY, false),
                        PaintProductProfile.Medium.WATER_BASED_ACRYLIC),
                "001", "Paint", new PaintProduct.Color("Blue", "#aabbcc"),
                PaintProductLifecycle.ACTIVE, "verified", List.of(), List.of(), null,
                null, image, 18, List.of(), PaintProduct.UsageInstructions.empty(), null,
                PaintProduct.ImageReference.empty(), List.of(), java.util.List.of(), "test-container");
    }

    private static PaintProduct.ImageQualityLimitation limitation() {
        return new PaintProduct.ImageQualityLimitation(
                PaintProductImageLimitationCode.HISTORICAL_REASON_NOT_RECORDED,
                "The precise historical reason was not recorded.", LocalDate.parse("2026-09-01"));
    }

    private static MarketPaintingGuide guide(
            List<String> sourceReferences, List<MarketPaintingGuide.Slot> slots) {
        return new MarketPaintingGuide(
                1, "guide", 1, MarketPaintingGuide.KnowledgeStatus.DOCUMENTED, "product-item",
                sourceReferences, new MarketPaintingGuide.Provenance("test", false), List.of(), slots,
                List.of(), List.of());
    }
}
