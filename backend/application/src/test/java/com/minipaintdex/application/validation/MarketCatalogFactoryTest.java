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
    void keepsOnePaintWithTwoSourcedCatalogMemberships() {
        var record = new java.util.LinkedHashMap<>(paint("paint"));
        record.put("catalog_memberships", List.of(membership("edition-2026"), membership("edition-2027")));
        var snapshot = MarketCatalogFactory.create(List.of(StructuredDocuments.fromMap(record)), List.of(), List.of(),
                List.of(edition("edition-2026", "Brand"), edition("edition-2027", "Brand")));
        assertEquals(1, snapshot.paints().size());
        assertEquals(2, snapshot.paints().getFirst().catalogMemberships().size());
        assertEquals(2, snapshot.paintCatalogEditions().size());
    }

    @Test
    void rejectsUnknownCrossBrandAndDuplicateMemberships() {
        var record = new java.util.LinkedHashMap<>(paint("paint"));
        record.put("catalog_memberships", List.of(membership("edition-2026")));
        var documents = List.of(StructuredDocuments.fromMap(record));
        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(documents, List.of(), List.of(), List.of()));
        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(documents, List.of(), List.of(),
                List.of(edition("edition-2026", "Another brand"))));
        record.put("catalog_memberships", List.of(membership("edition-2026"), membership("edition-2026")));
        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(List.of(StructuredDocuments.fromMap(record)),
                List.of(), List.of(), List.of(edition("edition-2026", "Brand"))));
    }

    @Test
    void rejectsUnsourcedEditionsAndMembershipsOutsideTheirScope() {
        assertThrows(DomainException.class, () -> MarketCatalogFactory.catalogEdition(StructuredDocuments.fromMap(Map.of(
                "schema_version", 1, "id", "edition", "brand", "Brand", "title", "Title", "edition_label", "Summer",
                "ranges", List.of("Range"), "source_urls", List.of()))));
        var record = new java.util.LinkedHashMap<>(paint("paint"));
        record.put("catalog_memberships", List.of(Map.of("catalog_edition_id", "edition", "source_url", "https://other.example/catalog", "locator", "p. 2")));
        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(List.of(StructuredDocuments.fromMap(record)),
                List.of(), List.of(), List.of(edition("edition", "Brand"))));
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
                List.of(StructuredDocuments.fromMap(guide("paint"))), List.of());

        assertEquals("paint", snapshot.paints().getFirst().id());
        assertEquals("one_coat_shading", snapshot.paints().getFirst().profile().applicationSystem().id());
        assertEquals("guide", snapshot.paintingGuides().getFirst().id());
    }

    @Test
    void rejectsCrossReferencesOutsideTheProspectiveGeneration() {
        var failure = assertThrows(DomainException.class, () -> MarketCatalogFactory.create(
                List.of(StructuredDocuments.fromMap(paint("paint"))),
                List.of(product()),
                List.of(StructuredDocuments.fromMap(guide("unknown-paint"))), List.of()));

        assertEquals("invalid_market_catalog", failure.code());
    }

    @Test
    void rejectsMalformedNumbersInsteadOfSilentlyUsingZero() {
        var malformed = new java.util.LinkedHashMap<>(paint("paint"));
        malformed.put("volume_ml", "many");

        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(
                List.of(StructuredDocuments.fromMap(malformed)), List.of(product()),
                List.of(StructuredDocuments.fromMap(guide("paint"))), List.of()));
    }

    @Test
    void rejectsAPaintWithoutTheCurrentSchemaVersion() {
        var missingVersion = new java.util.LinkedHashMap<>(paint("paint"));
        missingVersion.remove("schema_version");

        assertThrows(DomainException.class, () -> MarketCatalogFactory.create(
                List.of(StructuredDocuments.fromMap(missingVersion)), List.of(product()),
                List.of(StructuredDocuments.fromMap(guide("paint"))), List.of()));
    }

    @Test
    void rejectsWorkshopReferencesOutsideTheValidatedMarketGeneration() {
        var snapshot = new DataSnapshot(
                new StructuredDocument(List.of()), List.of(StructuredDocuments.fromMap(paint("paint"))),
                List.of(StructuredDocuments.fromMap(Map.of("paint_id", "unknown", "quantity", 1))),
                List.of(product()), List.of(StructuredDocuments.fromMap(guide("paint"))),
                List.of(), List.of(), List.of());

        assertThrows(DomainException.class, () -> DataSnapshotValidator.validate(snapshot));
    }

    @Test
    void rejectsAnonymousShoppingIntentWithoutAMarketReference() {
        var snapshot = new DataSnapshot(
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
                Map.entry("name", "Paint"), Map.entry("volume_ml", 18),
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
                List.of(new PaintableProduct.CatalogItem(
                        "product-item", "product", "Item", "miniature", 1, "", false,
                        List.of(), List.of())));
    }

    private static Map<String, Object> guide(String paintId) {
        return Map.ofEntries(
                Map.entry("id", "guide"), Map.entry("version", 1),
                Map.entry("knowledge_status", "documented"),
                Map.entry("catalog_item_id", "product-item"), Map.entry("source_refs", List.of("source")),
                Map.entry("slots", List.of(Map.of(
                        "id", "base", "role", "Base coat", "market_paint_id", paintId))));
    }
}
