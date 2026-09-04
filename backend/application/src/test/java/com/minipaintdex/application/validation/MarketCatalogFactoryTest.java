package com.minipaintdex.application.validation;

import com.minipaintdex.domain.market.product.PaintableProduct;
import com.minipaintdex.domain.shared.DomainException;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.document.StructuredDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketCatalogFactoryTest {
    @Test
    void sharedGuidesRequireExplicitSameBrandAndRangeLinks() {
        var record = new java.util.LinkedHashMap<>(paint("paint"));
        record.put("usage_guide_ids", List.of("usage"));
        var document = StructuredDocuments.fromMap(record);
        var guide = new java.util.LinkedHashMap<String, Object>();
        guide.put("schema_version", 1); guide.put("id", "usage"); guide.put("brand", "Brand"); guide.put("title", "Usage");
        guide.put("revision", 1); guide.put("ranges", List.of("Range")); guide.put("original_language", "en");
        guide.put("original", Map.of("summary", "Guidance", "steps", List.of("Shake"), "tips", List.of("Care")));
        guide.put("knowledge_status", "generic-template"); guide.put("review_required", true);
        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(List.of(document), List.of(), List.of(), List.of(), List.of()));
        var snapshot = MarketCatalogFactory.create(List.of(document), List.of(), List.of(), List.of(), List.of(StructuredDocuments.fromMap(guide)));
        assertEquals(List.of("usage"), snapshot.paints().getFirst().usageGuideIds());
        guide.put("brand", "Other");
        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(List.of(document), List.of(), List.of(), List.of(), List.of(StructuredDocuments.fromMap(guide))));
        guide.put("brand", "Brand"); guide.put("ranges", List.of("Other"));
        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(List.of(document), List.of(), List.of(), List.of(), List.of(StructuredDocuments.fromMap(guide))));
    }

    @Test
    void keepsOnePaintWithTwoSourcedCatalogMemberships() {
        var record = new java.util.LinkedHashMap<>(paint("paint"));
        record.put("catalog_memberships", List.of(membership("edition-2026"), membership("edition-2027")));
        var snapshot = MarketCatalogFactory.create(List.of(StructuredDocuments.fromMap(record)), List.of(), List.of(),
                List.of(edition("edition-2026", "Brand"), edition("edition-2027", "Brand")), java.util.List.of());
        assertEquals(1, snapshot.paints().size());
        assertEquals(2, snapshot.paints().getFirst().catalogMemberships().size());
        assertEquals(2, snapshot.paintCatalogEditions().size());
    }

    @Test
    void rejectsUnknownCrossBrandAndDuplicateMemberships() {
        var record = new java.util.LinkedHashMap<>(paint("paint"));
        record.put("catalog_memberships", List.of(membership("edition-2026")));
        var documents = List.of(StructuredDocuments.fromMap(record));
        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(documents, List.of(), List.of(), List.of(), java.util.List.of()));
        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(documents, List.of(), List.of(),
                List.of(edition("edition-2026", "Another brand")), java.util.List.of()));
        record.put("catalog_memberships", List.of(membership("edition-2026"), membership("edition-2026")));
        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(List.of(StructuredDocuments.fromMap(record)),
                List.of(), List.of(), List.of(edition("edition-2026", "Brand")), java.util.List.of()));
    }

    @Test
    void rejectsUnsourcedEditionsAndMembershipsOutsideTheirScope() {
        assertThrows(DomainException.class, () -> MarketCatalogFactory.catalogEdition(StructuredDocuments.fromMap(Map.of(
                "schema_version", 1, "id", "edition", "brand", "Brand", "title", "Title", "edition_label", "Summer",
                "ranges", List.of("Range"), "source_urls", List.of()))));
        var record = new java.util.LinkedHashMap<>(paint("paint"));
        record.put("catalog_memberships", List.of(Map.of("catalog_edition_id", "edition", "source_url", "https://other.example/catalog", "locator", "p. 2")));
        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(List.of(StructuredDocuments.fromMap(record)),
                List.of(), List.of(), List.of(edition("edition", "Brand")), java.util.List.of()));
    }

    private static Map<String, Object> membership(String id) {
        return Map.of("catalog_edition_id", id, "source_url", "https://example.com/catalog", "locator", "page 2");
    }
    private static StructuredDocument edition(String id, String brand) {
        return StructuredDocuments.fromMap(Map.of("schema_version", 1, "id", id, "brand", brand,
                "title", "Product catalogue", "edition_label", id, "ranges", List.of("Range"),
                "source_urls", List.of("https://example.com/catalog")));
    }

    @Test
    void translatesOneCoherentGenerationIntoTypedAggregates() {
        var snapshot = MarketCatalogFactory.create(
                List.of(StructuredDocuments.fromMap(paint("paint"))),
                List.of(product()),
                List.of(StructuredDocuments.fromMap(guide("paint"))), List.of(), java.util.List.of());

        assertEquals("paint", snapshot.paints().getFirst().id());
        assertEquals("one_coat_shading", snapshot.paints().getFirst().profile().applicationSystem().id());
        assertEquals("guide", snapshot.paintingGuides().getFirst().id());
    }

    @Test
    void rejectsCrossReferencesOutsideTheProspectiveGeneration() {
        var failure = assertThrows(DomainException.class, () -> MarketCatalogFactory.create(
                List.of(StructuredDocuments.fromMap(paint("paint"))),
                List.of(product()),
                List.of(StructuredDocuments.fromMap(guide("unknown-paint"))), List.of(), java.util.List.of()));

        assertEquals("invalid_market_catalog", failure.code());
    }

    @Test
    void rejectsMalformedNumbersInsteadOfSilentlyUsingZero() {
        var malformed = new java.util.LinkedHashMap<>(paint("paint"));
        malformed.put("volume_ml", "many");

        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(
                List.of(StructuredDocuments.fromMap(malformed)), List.of(product()),
                List.of(StructuredDocuments.fromMap(guide("paint"))), List.of(), java.util.List.of()));
    }

    @Test
    void rejectsAPaintWithoutTheCurrentSchemaVersion() {
        var missingVersion = new java.util.LinkedHashMap<>(paint("paint"));
        missingVersion.remove("schema_version");

        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(
                List.of(StructuredDocuments.fromMap(missingVersion)), List.of(product()),
                List.of(StructuredDocuments.fromMap(guide("paint"))), List.of(), java.util.List.of()));
    }

    @Test
    void rejectsWorkshopReferencesOutsideTheValidatedMarketGeneration() {
        var snapshot = snapshot(
                new StructuredDocument(List.of()), List.of(StructuredDocuments.fromMap(paint("paint"))),
                List.of(StructuredDocuments.fromMap(Map.of("paint_id", "unknown", "quantity", 1))),
                List.of(product()), List.of(StructuredDocuments.fromMap(guide("paint"))),
                List.of(), List.of(), List.of());

        assertThrows(DomainException.class, () -> DataSnapshotValidator.validate(snapshot));
    }

    @Test
    void rejectsAnonymousShoppingIntentWithoutAMarketReference() {
        var snapshot = snapshot(
                new StructuredDocument(List.of()), List.of(StructuredDocuments.fromMap(paint("paint"))),
                List.of(), List.of(product()), List.of(StructuredDocuments.fromMap(guide("paint"))),
                List.of(StructuredDocuments.fromMap(Map.of("id", "buy-1", "priority", "low"))), List.of(), List.of());

        assertThrows(DomainException.class, () -> DataSnapshotValidator.validate(snapshot));
    }

    private static Map<String, Object> paint(String id) {
        return Map.ofEntries(
                Map.entry("schema_version", 1), Map.entry("id", id),
                Map.entry("brand", "Brand"), Map.entry("manufacturer", "Maker"),
                Map.entry("range", "Range"), Map.entry("profile", Map.of(
                        "roles", List.of("color_paint"), "application_methods", List.of("brush"),
                        "application_system", "one_coat_shading", "coverage", "transparent",
                        "finish", "matte", "effects", List.of(),
                        "undercoat", Map.of("tone", "light", "pre_highlighted_surface_recommended", true),
                        "medium", "acrylic")),
                Map.entry("name", "Paint"), Map.entry("container_format_id", "standard"), Map.entry("volume_ml", 18),
                Map.entry("tags", List.of("smooth")),
                Map.entry("manufacturer_image", Map.of(
                        "image_quality", "none",
                        "quality_limitation", Map.of(
                                "code", "historical-reason-not-recorded",
                                "detail", "The precise historical reason was not recorded.",
                                "observed_at", "2026-09-01"))));
    }

    private static PaintableProduct product() {
        return new PaintableProduct(
                1, "product", "Product", "Line", "board_game", "Core", 1,
                new PaintableProduct.Edition("", ""), List.of(),
                List.of(new PaintableProduct.PaintableComponent(
                        "product-item", "product", "Item", "miniature", 1, "", false,
                        List.of(), List.of())));
    }

    private static Map<String, Object> guide(String paintProductId) {
        return Map.ofEntries(
                Map.entry("id", "guide"), Map.entry("version", 1),
                Map.entry("knowledge_status", "documented"),
                Map.entry("catalog_item_id", "product-item"), Map.entry("source_refs", List.of("source")),
                Map.entry("slots", List.of(Map.of(
                        "id", "base", "role", "Base coat", "market_paint_id", paintProductId))));
    }

    private static com.minipaintdex.application.port.DataSnapshot snapshot(
            com.minipaintdex.application.document.StructuredDocument site,
            java.util.List<com.minipaintdex.application.document.StructuredDocument> paints,
            java.util.List<com.minipaintdex.application.document.StructuredDocument> stocks,
            java.util.List<com.minipaintdex.domain.market.product.PaintableProduct> products,
            java.util.List<com.minipaintdex.application.document.StructuredDocument> guides,
            java.util.List<com.minipaintdex.application.document.StructuredDocument> shopping,
            java.util.List<com.minipaintdex.domain.event.EventEnvelope> history,
            java.util.List<com.minipaintdex.application.document.StructuredDocument> editions) {
        var events = new java.util.ArrayList<com.minipaintdex.domain.event.EventEnvelope>();
        for (var stock : com.minipaintdex.application.validation.StructuredDocuments.toMaps(stocks)) {
            var id = String.valueOf(stock.get("paint_id"));
            var quantity = ((Number) stock.get("quantity")).intValue();
            for (var ordinal = 1; ordinal <= quantity; ordinal++) {
                var potId = "pot-test-" + id + "-" + ordinal;
                if (history.stream().anyMatch(event -> potId.equals(event.aggregateId()))) continue;
                var pot = com.minipaintdex.domain.workshop.PaintPot.register(potId, id, null, java.time.Instant.EPOCH);
                events.add(new com.minipaintdex.domain.event.EventEnvelope(potId, 1, 1, java.time.Instant.EPOCH,
                        new com.minipaintdex.domain.event.Actor("user", "owner"), "fixture", null, potId, pot.releaseEvents().getFirst()));
            }
        }
        events.addAll(history);
        return new DataSnapshot(site, paints, products, guides, shopping, events, editions, java.util.List.of(), new com.minipaintdex.domain.market.storage.RackCatalog(1, 1, List.of(new com.minipaintdex.domain.market.storage.PaintContainerFormat(1, "standard", "Standard", "Test", "dropper", null, com.minipaintdex.domain.shared.storage.ContainerDimensions.unknown(), "unknown", List.of(), "")), List.of()));
    }

}
