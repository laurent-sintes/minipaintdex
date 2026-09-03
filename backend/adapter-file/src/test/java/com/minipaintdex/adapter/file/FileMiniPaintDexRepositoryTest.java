package com.minipaintdex.adapter.file;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.workshop.WorkshopPaintableCommentAdded;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMiniPaintDexRepositoryTest {
    @TempDir
    Path root;

    @Test
    void keepsSharedGuidesAcrossPaintWritesAndRestart() throws IOException {
        createFixture();
        var repository = new FileMiniPaintDexRepository(layout());
        repository.initialize();
        var before = repository.load();
        var raw = new LinkedHashMap<String, Object>();
        raw.put("schema_version", 1); raw.put("id", "brand-usage"); raw.put("brand", "Brand");
        raw.put("title", "Range"); raw.put("revision", 1); raw.put("ranges", List.of("Range"));
        raw.put("original_language", "en"); raw.put("knowledge_status", "generic-template"); raw.put("review_required", true);
        raw.put("original", Map.of("summary", "Usage", "steps", List.of("Shake"), "tips", List.of("Care")));
        var guide = document(raw);
        repository.replacePaintProductCatalog(before.paintProducts(), before.paintCatalogEditions(), List.of(guide));
        repository.replacePaintProducts(before.paintProducts());
        assertEquals(List.of(guide), repository.load().paintUsageGuides());
        var reopened = new FileMiniPaintDexRepository(layout());
        reopened.initialize();
        assertEquals(List.of(guide), reopened.load().paintUsageGuides());
        assertEquals(before.events(), reopened.load().events());
    }

    @Test
    void loadsAnIsolatedFileRepository() throws IOException {
        createFixture();
        var repository = new FileMiniPaintDexRepository(layout());
        repository.initialize();

        var snapshot = repository.load();

        assertEquals(1, snapshot.paintProducts().size());
        assertEquals(1, snapshot.paintableProducts().size());
        assertEquals(1, snapshot.marketPaintingGuides().size());
        assertEquals(5, snapshot.events().size());
        assertEquals("workshop.created", snapshot.events().getFirst().eventType());
    }

    @Test
    void replaysEqualTimestampEventsInLedgerOrderRatherThanIdentifierOrder() throws IOException {
        createFixture();
        var ledger = root.resolve("data/ledger/events/2026-08.jsonl");
        Files.writeString(ledger, Files.readString(ledger)
                .replaceAll("2026-08-30T[0-9:]+Z", "2026-08-30T10:00:00Z")
                .replace("01KTESTPROJECT000000000001", "zz-project-created")
                .replace("01KTESTPROJECT000000000002", "aa-project-activated"));
        var repository = new FileMiniPaintDexRepository(layout());

        repository.initialize();

        assertEquals(List.of("workshop.created", "painting_project.created", "painting_project.status_changed",
                        "workshop.painting_project_registered", "workshop_item.added"),
                repository.load().events().stream().map(EventEnvelope::eventType).toList());
    }

    @Test
    void preservesAppendOrderInPublishedSnapshotAndAfterRestart() throws IOException {
        createFixture();
        var repository = new FileMiniPaintDexRepository(layout());
        repository.initialize();
        var existingIds = repository.load().events().stream().map(EventEnvelope::eventId).toList();
        var recordedAt = Instant.parse("2026-08-30T09:00:00Z");
        var first = new EventEnvelope("zz-first-comment", 1, 2, recordedAt,
                new Actor("user", "owner"), "correlation", null, "first-comment",
                new WorkshopPaintableCommentAdded("ws-1", "paint-game", "First", recordedAt));
        var second = new EventEnvelope("aa-second-comment", 1, 3, recordedAt,
                new Actor("user", "owner"), "correlation", null, "second-comment",
                new WorkshopPaintableCommentAdded("ws-1", "paint-game", "Second", recordedAt));
        var expectedIds = new ArrayList<>(existingIds);
        expectedIds.addAll(List.of(first.eventId(), second.eventId()));

        repository.appendAll(List.of(first, second));

        assertEquals(expectedIds, repository.load().events().stream().map(EventEnvelope::eventId).toList());
        var restarted = new FileMiniPaintDexRepository(layout());
        restarted.initialize();
        assertEquals(expectedIds, restarted.load().events().stream().map(EventEnvelope::eventId).toList());
    }

    @Test
    void preservesEditionsAcrossPaintReplacementAndRestart() throws IOException {
        createFixture();
        var repository = new FileMiniPaintDexRepository(layout());
        repository.initialize();
        var edition = document(Map.of("schema_version", 1, "id", "brand-2019", "brand", "Brand", "title", "Catalogue",
                "edition_label", "2019", "ranges", List.of("Range"), "source_urls", List.of("https://example.com/catalog.pdf")));
        repository.replacePaintProductCatalog(repository.load().paintProducts(), List.of(edition), java.util.List.of());
        repository.replacePaintProducts(repository.load().paintProducts());
        var restarted = new FileMiniPaintDexRepository(layout());
        restarted.initialize();
        assertEquals(List.of(edition), restarted.load().paintCatalogEditions());
        assertEquals(1, restarted.load().paintProducts().size());
    }

    @Test
    void supportsConcurrentSnapshotReads() throws Exception {
        createFixture();
        var repository = new FileMiniPaintDexRepository(layout());
        repository.initialize();
        List<Callable<Integer>> reads = IntStream.range(0, 32)
                .mapToObj(ignored -> (Callable<Integer>) () -> repository.load().paintProducts().size())
                .toList();

        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var result : executor.invokeAll(reads)) assertEquals(1, result.get());
        }
    }

    @Test
    void rejectsCatalogEnvelopeVersionsOtherThanOne() throws IOException {
        createFixture();
        write("data/market/paints/brand.yaml", "schema_version: 2\nbrand: Brand\npaints: []\n");

        assertThrows(FileStorageException.class, () -> new FileMiniPaintDexRepository(layout()).initialize());
    }

    @Test
    void atomicallyReplacesThePaintProductCatalog() throws IOException {
        createFixture();
        var repository = new FileMiniPaintDexRepository(layout());
        repository.initialize();

        repository.replacePaintProducts(List.of(document(Map.of(
                "schema_version", 1, "id", "paint",
                "brand", "Brand",
                "manufacturer", "Maker",
                "range", "Range",
                "profile", Map.of(
                        "roles", List.of("color_paint"), "application_methods", List.of("brush"),
                        "application_system", "conventional_layering", "coverage", "opaque",
                        "finish", "matte", "effects", List.of(),
                        "undercoat", Map.of("tone", "any", "pre_highlighted_surface_recommended", false),
                        "medium", "acrylic"),
                "name", "New Paint", "manufacturer_image", missingImage()))));

        var snapshot = repository.load();
        assertEquals("New Paint", text(snapshot.paintProducts().getFirst(), "name"));
        var stored = Files.readString(root.resolve("data/market/paints/brand.yaml"));
        assertTrue(stored.contains("schema_version: 1"));
        assertTrue(stored.contains("brand: Brand"));
        assertFalse(stored.contains("&id"));
    }

    @Test
    void rewritesOnlyThePaintProductBrandThatChanged() throws IOException {
        createFixture();
        write("data/market/paints/other-brand.yaml", """
                schema_version: 1
                brand: Other Brand
                paints:
                  - schema_version: 1
                    name: Other Paint
                    profile:
                      medium: acrylic
                      undercoat: {pre_highlighted_surface_recommended: false, tone: any}
                      effects: []
                      finish: matte
                      coverage: opaque
                      application_system: conventional_layering
                      application_methods: [brush]
                      roles: [color_paint]
                    range: Other Range
                    manufacturer: Other Maker
                    brand: Other Brand
                    id: other-paint
                    manufacturer_image:
                      image_quality: none
                      quality_limitation:
                        code: historical-reason-not-recorded
                        detail: The precise historical reason was not recorded.
                        observed_at: '2026-09-01'
                """);
        var repository = new FileMiniPaintDexRepository(layout());
        repository.initialize();
        var otherBrandPath = root.resolve("data/market/paints/other-brand.yaml");
        var otherBrandBefore = Files.readString(otherBrandPath);
        var paints = new ArrayList<>(repository.load().paintProducts());
        paints.removeIf(paint -> "paint".equals(text(paint, "id")));
        paints.add(document(Map.of(
                "schema_version", 1, "id", "paint", "brand", "Brand", "manufacturer", "Maker", "range", "Range",
                "profile", Map.of(
                        "roles", List.of("color_paint"), "application_methods", List.of("brush"),
                        "application_system", "conventional_layering", "coverage", "opaque",
                        "finish", "matte", "effects", List.of(),
                        "undercoat", Map.of("tone", "any", "pre_highlighted_surface_recommended", false),
                        "medium", "acrylic"),
                "name", "Updated Paint", "manufacturer_image", missingImage())));

        repository.replacePaintProducts(paints);

        assertEquals(otherBrandBefore, Files.readString(otherBrandPath));
        assertEquals("Updated Paint", repository.load().paintProducts().stream()
                .filter(paint -> "paint".equals(text(paint, "id")))
                .map(paint -> text(paint, "name"))
                .findFirst().orElseThrow());
    }

    @Test
    void enforcesLedgerIdempotencyInsideTheRepositoryLock() throws IOException {
        createFixture();
        var repository = new FileMiniPaintDexRepository(layout());
        repository.initialize();
        var now = Instant.parse("2026-08-30T11:00:00Z");
        var event = new EventEnvelope(
                "01KTESTCOMMENT000000000000", 1, 2, now,
                new Actor("user", "owner"), "correlation", null, "same-command",
                new WorkshopPaintableCommentAdded("ws-1", "paint-game", "Note", now));

        repository.append(event);
        var duplicate = repository.append(new EventEnvelope(
                "01KTESTCOMMENT000000000001", 1, 2, now,
                new Actor("user", "owner"), "other-correlation", null, "same-command",
                new WorkshopPaintableCommentAdded("ws-1", "paint-game", "Note", now)));

        assertEquals(event.eventId(), duplicate.eventId());
        assertEquals(6, repository.load().events().size());
    }

    @Test
    void rejectsAStaleAggregateVersionInsideTheRepositoryLock() throws IOException {
        createFixture();
        var repository = new FileMiniPaintDexRepository(layout());
        repository.initialize();
        var now = Instant.parse("2026-08-30T11:00:00Z");
        var stale = new EventEnvelope(
                "01KTESTSTALE00000000000000", 1, 3, now,
                new Actor("user", "owner"), "correlation", null, "stale-command",
                new WorkshopPaintableCommentAdded("ws-1", "paint-game", "Note", now));

        assertThrows(FileStorageException.class, () -> repository.append(stale));
        assertEquals(5, repository.load().events().size());
    }

    @Test
    void storesWorkshopMediaUnderTheConfiguredMediaRoot() throws IOException {
        createFixture();
        var repository = new FileMiniPaintDexRepository(layout());
        repository.initialize();

        var media = repository.store("ws-1", "media-1", "photo.png", "image/png", new byte[]{1, 2, 3});

        assertEquals("/media/workshop/ws-1/media-1.png", media.publicPath());
        assertTrue(Files.exists(root.resolve("media/workshop/ws-1/media-1.png")));
    }

    @Test
    void refreshesCachesOnlyAfterAValidExternalChange() throws IOException {
        createFixture();
        var repository = new FileMiniPaintDexRepository(layout());
        repository.initialize();
        write("data/market/paints/brand.yaml", """
                schema_version: 1
                brand: Brand
                paints:
                  - schema_version: 1
                    id: paint
                    brand: Brand
                    manufacturer: Maker
                    range: Range
                    profile: &profile
                      roles: [color_paint]
                      application_methods: [brush]
                      application_system: conventional_layering
                      coverage: opaque
                      finish: matte
                      effects: []
                      undercoat: {tone: any, pre_highlighted_surface_recommended: false}
                      medium: acrylic
                    name: Refreshed Paint
                    manufacturer_image:
                      image_quality: none
                      quality_limitation:
                        code: historical-reason-not-recorded
                        detail: The precise historical reason was not recorded.
                        observed_at: '2026-09-01'
                """);

        assertEquals("paint", text(repository.load().paintProducts().getFirst(), "id"));
        var refresh = repository.refreshIfChanged();

        assertTrue(refresh.changed());
        assertEquals("ready", refresh.status().state());
        assertEquals("Refreshed Paint", text(repository.load().paintProducts().getFirst(), "name"));
    }

    @Test
    void retainsTheLastValidGenerationAndRejectsWritesWhenExternalDataIsInvalid() throws IOException {
        createFixture();
        var repository = new FileMiniPaintDexRepository(layout());
        repository.initialize();
        write("data/market/paints/brand.yaml", """
                schema_version: 1
                brand: Brand
                paints:
                  - schema_version: 1
                    id: invalid-paint
                    brand: Brand
                    range: Range
                    profile:
                      roles: [color_paint]
                      application_methods: [brush]
                      application_system: conventional_layering
                      coverage: opaque
                      finish: matte
                      effects: []
                      undercoat: {tone: any, pre_highlighted_surface_recommended: false}
                      medium: acrylic
                    name: Invalid Paint
                """);

        var refresh = repository.refreshIfChanged();

        assertFalse(refresh.changed());
        assertEquals("degraded", refresh.status().state());
        assertEquals("paint", text(repository.load().paintProducts().getFirst(), "id"));
        assertThrows(FileStorageException.class, () -> repository.replacePaintProducts(List.of()));
    }

    private void createFixture() throws IOException {
        write("data/site/fr.yaml", "metadata: {}\n");
        write("data/market/paints/brand.yaml", """
                schema_version: 1
                brand: Brand
                paints:
                  - schema_version: 1
                    id: paint
                    brand: Brand
                    manufacturer: Maker
                    range: Range
                    profile:
                      roles: [color_paint]
                      application_methods: [brush]
                      application_system: conventional_layering
                      coverage: opaque
                      finish: matte
                      effects: []
                      undercoat: {tone: any, pre_highlighted_surface_recommended: false}
                      medium: acrylic
                    name: Paint
                    manufacturer_image:
                      image_quality: none
                      quality_limitation:
                        code: historical-reason-not-recorded
                        detail: The precise historical reason was not recorded.
                        observed_at: '2026-09-01'
                """);
        write("data/workshop/paints.yaml", "schema_version: 1\npaints: []\n");
        write("data/workshop/shopping.yaml", "schema_version: 1\nitems: []\n");
        write("data/market/paintable-products/game.yaml", """
                schema_version: 1
                id: game
                name: Game
                line: Game
                product_type: board_game
                scope: Core
                expected_paintable_count: 1
                catalog_items:
                  - id: game-hero
                    product_id: game
                    name: Hero
                    kind: hero
                    quantity: 1
                """);
        write("data/market/painting-guides/game.yaml", """
                schema_version: 1
                painting_guides:
                  - id: game-hero-guide
                    version: 1
                    knowledge_status: documented
                    catalog_item_id: game-hero
                    sources:
                      - kind: test_fixture
                        label: Repository fixture
                    slots:
                      - id: base-color
                        role: Base color
                        market_paint_id: paint
                """);
        write("data/ledger/events/2026-08.jsonl", """
                {"event_id":"01KTESTWORKSHOP00000000000","schema_version":1,"aggregate_version":1,"event_type":"workshop.created","occurred_at":"2026-08-30T09:56:00Z","recorded_at":"2026-08-30T09:56:00Z","aggregate_type":"workshop","aggregate_id":"my-workshop","actor":{"type":"user","id":"owner"},"correlation_id":"correlation","idempotency_key":"workshop","payload":{"name":"My workshop"}}
                {"event_id":"01KTESTPROJECT000000000001","schema_version":1,"aggregate_version":1,"event_type":"painting_project.created","occurred_at":"2026-08-30T09:57:00Z","recorded_at":"2026-08-30T09:57:00Z","aggregate_type":"painting_project","aggregate_id":"paint-game","project_id":"paint-game","actor":{"type":"user","id":"owner"},"correlation_id":"correlation","idempotency_key":"project","payload":{"workshop_id":"my-workshop","paintable_product_id":"game","name":"Paint Game","paintable_item_count":1}}
                {"event_id":"01KTESTPROJECT000000000002","schema_version":1,"aggregate_version":2,"event_type":"painting_project.status_changed","occurred_at":"2026-08-30T09:58:00Z","recorded_at":"2026-08-30T09:58:00Z","aggregate_type":"painting_project","aggregate_id":"paint-game","project_id":"paint-game","actor":{"type":"user","id":"owner"},"correlation_id":"correlation","idempotency_key":"project-active","payload":{"status":"active"}}
                {"event_id":"01KTESTREGISTER00000000001","schema_version":1,"aggregate_version":2,"event_type":"workshop.painting_project_registered","occurred_at":"2026-08-30T09:59:00Z","recorded_at":"2026-08-30T09:59:00Z","aggregate_type":"workshop","aggregate_id":"my-workshop","actor":{"type":"user","id":"owner"},"correlation_id":"correlation","idempotency_key":"register","payload":{"painting_project_id":"paint-game"}}
                {"event_id":"01KTESTEVENT00000000000000","schema_version":1,"aggregate_version":1,"event_type":"workshop_item.added","occurred_at":"2026-08-30T10:00:00Z","recorded_at":"2026-08-30T10:00:00Z","aggregate_type":"workshop_item","aggregate_id":"ws-1","project_id":"paint-game","actor":{"type":"user","id":"owner"},"correlation_id":"correlation","payload":{"catalog_item_id":"game-hero","painting_project_id":"paint-game","display_name":"Hero","ordinal":1}}
                """);
    }

    private static StructuredDocument document(Map<String, Object> values) {
        return new StructuredDocument(values.entrySet().stream()
                .map(entry -> new StructuredDocument.Field(
                        entry.getKey(), documentValue(entry.getValue())))
                .toList());
    }

    private static Map<String, Object> missingImage() {
        return Map.of(
                "image_quality", "none",
                "quality_limitation", Map.of(
                        "code", "historical-reason-not-recorded",
                        "detail", "The precise historical reason was not recorded.",
                        "observed_at", "2026-09-01"));
    }

    private static StructuredDocument.Value documentValue(Object value) {
        if (value instanceof Map<?, ?> values) {
            var normalized = new LinkedHashMap<String, Object>();
            values.forEach((key, entry) -> normalized.put(String.valueOf(key), entry));
            return new StructuredDocument.ObjectValue(document(normalized));
        }
        if (value instanceof List<?> values) {
            return new StructuredDocument.ArrayValue(values.stream()
                    .map(FileMiniPaintDexRepositoryTest::documentValue).toList());
        }
        if (value instanceof Number number) return new StructuredDocument.NumberValue(number);
        if (value instanceof Boolean bool) return new StructuredDocument.BooleanValue(bool);
        if (value == null) return new StructuredDocument.NullValue();
        return new StructuredDocument.Text(String.valueOf(value));
    }

    private static String text(StructuredDocument document, String fieldName) {
        return document.fields().stream().filter(field -> fieldName.equals(field.name()))
                .map(StructuredDocument.Field::value)
                .map(value -> value instanceof StructuredDocument.Text text ? text.value() : String.valueOf(value))
                .findFirst().orElse("");
    }

    private FileRepositoryLayout layout() {
        return new FileRepositoryLayout(
                root.resolve("data/site/fr.yaml"),
                root.resolve("data/market/paints"),
                root.resolve("data/workshop/shopping.yaml"),
                root.resolve("data/market/paintable-products"),
                root.resolve("data/market/painting-guides"),
                root.resolve("data/ledger/events"),
                root.resolve("data/ledger/publications"),
                root.resolve("media"));
    }

    private void write(String relativePath, String content) throws IOException {
        var path = root.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
