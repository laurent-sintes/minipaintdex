package com.minipaintdex.adapter.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMiniPaintDexRepositoryTest {
    @TempDir
    Path root;

    @Test
    void loadsAnIsolatedFileRepository() throws IOException {
        createFixture();
        var repository = new FileMiniPaintDexRepository(layout());

        var snapshot = repository.load();

        assertEquals(1, snapshot.marketPaints().size());
        assertEquals(1, snapshot.paintableProducts().size());
        assertEquals(1, snapshot.marketPaintingGuides().size());
        assertEquals(1, snapshot.events().size());
        assertEquals("workshop_item.added", snapshot.events().getFirst().eventType());
    }

    @Test
    void atomicallyReplacesTheMarketPaintCatalog() throws IOException {
        createFixture();
        var repository = new FileMiniPaintDexRepository(layout());

        repository.replaceMarketPaints(List.of(Map.of(
                "id", "new-paint",
                "brand", "Brand",
                "manufacturer", "Maker",
                "range", "Range",
                "functional_type", "opaque_standard",
                "name", "New Paint")));

        var snapshot = repository.load();
        assertEquals("new-paint", snapshot.marketPaints().getFirst().get("id"));
        assertTrue(Files.readString(root.resolve("data/market/paints/catalog.yaml")).contains("schema_version: 1"));
    }

    private void createFixture() throws IOException {
        write("data/site/fr.yaml", "metadata: {}\n");
        write("data/market/paints/catalog.yaml", """
                schema_version: 1
                paints:
                  - id: paint
                    brand: Brand
                    manufacturer: Maker
                    range: Range
                    functional_type: opaque_standard
                    name: Paint
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
                    catalog_item_id: game-hero
                """);
        write("data/ledger/events/2026-08.jsonl", """
                {"event_id":"01KTESTEVENT00000000000000","schema_version":1,"event_type":"workshop_item.added","occurred_at":"2026-08-30T10:00:00Z","recorded_at":"2026-08-30T10:00:00Z","aggregate_type":"workshop_item","aggregate_id":"ws-1","project_id":"game","actor":{"type":"user","id":"owner"},"correlation_id":"correlation","payload":{"catalog_item_id":"game-hero","display_name":"Hero"}}
                """);
    }

    private FileRepositoryLayout layout() {
        return new FileRepositoryLayout(
                root.resolve("data/site/fr.yaml"),
                root.resolve("data/market/paints/catalog.yaml"),
                root.resolve("data/workshop/paints.yaml"),
                root.resolve("data/workshop/shopping.yaml"),
                root.resolve("data/market/paintable-products"),
                root.resolve("data/market/painting-guides"),
                root.resolve("data/ledger/events"),
                root.resolve("media"));
    }

    private void write(String relativePath, String content) throws IOException {
        var path = root.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
