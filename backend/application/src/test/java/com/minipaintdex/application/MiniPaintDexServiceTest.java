package com.minipaintdex.application;

import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.ApplyMarketPaintChangeSetCommand;
import com.minipaintdex.application.command.ApplyMiniatureProjectChangeSetCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventLedger;
import com.minipaintdex.application.port.MarketPaintCatalogWriter;
import com.minipaintdex.application.port.ProjectCatalogWriter;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.WorkshopPaintInventoryWriter;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.workflow.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniPaintDexServiceTest {
    @Test
    void searchesEverySupportedMarketFacet() {
        var repository = repository();
        var service = new MiniPaintDexService(repository, repository, repository, repository, repository);

        var result = service.searchMarketPaints(new SearchMarketPaintsQuery(
                "white", "Warhammer Colour", "Contrast", "one_coat_contrast", "White",
                "matt", "water acrylic", "transparent", "18", "29-34", "current",
                "Games Workshop", "cold"));

        assertEquals(1, result.size());
        assertEquals("Apothecary White", result.getFirst().get("name"));
    }

    @Test
    void rejectsAnItemOutsideTheReferencedProjectCatalog() {
        var repository = repository();
        var service = new MiniPaintDexService(repository, repository, repository, repository, repository);

        var exception = assertThrows(DomainException.class, () -> service.addWorkshopItem(
                new AddWorkshopItemCommand("ws-2", "unknown", "game", "Unknown", null, null, null, "key")));

        assertEquals("not_found", exception.code());
        assertTrue(repository.appended.isEmpty());
    }

    @Test
    void returnsTheExistingEventForAnIdempotentMutation() {
        var repository = repository();
        var existing = event("ws-1", "key");
        repository.snapshot = snapshot(List.of(existing));
        var service = new MiniPaintDexService(repository, repository, repository, repository, repository);

        var result = service.addWorkshopItem(
                new AddWorkshopItemCommand("ws-1", "game-hero", "game", "Hero", null, null, null, "key"));

        assertEquals(existing, result);
        assertTrue(repository.appended.isEmpty());
    }

    @Test
    void previewsThenAppliesAMarketPaintChangeSet() {
        var repository = repository();
        var service = new MiniPaintDexService(repository, repository, repository, repository, repository);
        var record = paint("new-paint", "New Paint");
        var operation = new ApplyMarketPaintChangeSetCommand.Operation("upsert", record, 2, false);

        var preview = service.applyMarketPaintChangeSet(
                new ApplyMarketPaintChangeSetCommand(1, "market_paints", List.of(operation), true));
        assertFalse(preview.applied());
        assertEquals(1, preview.added());
        assertTrue(repository.replaced.isEmpty());
        assertTrue(repository.inventory.isEmpty());

        var applied = service.applyMarketPaintChangeSet(
                new ApplyMarketPaintChangeSetCommand(1, "market_paints", List.of(operation), false));
        assertTrue(applied.applied());
        assertEquals(2, repository.replaced.size());
        assertEquals(2, repository.inventory.getFirst().get("quantity"));
    }

    @Test
    void protectsOwnedPaintsFromDeletion() {
        var repository = repository();
        repository.snapshot = new DataSnapshot(
                Map.of(), repository.snapshot.marketPaints(),
                List.of(Map.of("paint_id", "warhammer-colour-contrast-apothecary-white", "quantity", 1)),
                repository.snapshot.games(), List.of(), List.of(), List.of());
        var service = new MiniPaintDexService(repository, repository, repository, repository, repository);
        var operation = new ApplyMarketPaintChangeSetCommand.Operation(
                "delete", Map.of("id", "warhammer-colour-contrast-apothecary-white"), 0, true);

        var exception = assertThrows(DomainException.class, () -> service.applyMarketPaintChangeSet(
                new ApplyMarketPaintChangeSetCommand(1, "market_paints", List.of(operation), false)));

        assertEquals("conflict", exception.code());
        assertTrue(repository.replaced.isEmpty());
    }

    @Test
    void rejectsTechnicalPaintWithoutUsageInstructions() {
        var repository = repository();
        var service = new MiniPaintDexService(repository, repository, repository, repository, repository);
        var technical = new java.util.LinkedHashMap<>(paint("technical-paint", "Technical Paint"));
        technical.put("functional_type", "technical_effect");
        var operation = new ApplyMarketPaintChangeSetCommand.Operation("upsert", technical, 0, false);

        var exception = assertThrows(DomainException.class, () -> service.applyMarketPaintChangeSet(
                new ApplyMarketPaintChangeSetCommand(1, "market_paints", List.of(operation), true)));

        assertEquals("invalid_input", exception.code());
        assertTrue(repository.replaced.isEmpty());
    }

    @Test
    void previewsAMiniatureProjectWithoutWritingFilesOrEvents() {
        var repository = repository();
        var service = new MiniPaintDexService(repository, repository, repository, repository, repository);
        var project = Map.<String, Object>of(
                "id", "new-game",
                "name", "New Game",
                "game", "New Game",
                "scope", "full-set",
                "expected_paintable_count", 1,
                "catalog_items", List.of(Map.of(
                        "id", "new-game-hero", "game_id", "new-game", "name", "Hero", "kind", "miniature")));
        var item = new ApplyMiniatureProjectChangeSetCommand.WorkshopItem(
                "ws-new-game-hero-001", "new-game-hero", "new-game", "Hero #1");

        var result = service.applyMiniatureProjectChangeSet(new ApplyMiniatureProjectChangeSetCommand(
                1, "miniature_project", project, List.of(), List.of(item), true, "owner", "import-new-game"));

        assertFalse(result.applied());
        assertEquals(1, result.workshopItemsAdded());
        assertEquals(0, repository.projectsWritten);
        assertTrue(repository.appended.isEmpty());
    }

    @Test
    void reconcilesAgainstOwnedPaintsAndFlagsBehavioralProducts() {
        var repository = repository();
        var alternative = paint("owned-alternative", "Owned Alternative");
        var guide = Map.<String, Object>of(
                "id", "game-hero-guide", "version", 1, "knowledge_status", "documented",
                "catalog_item_id", "game-hero",
                "slots", List.of(Map.of("id", "game-hero-guide-slot-01", "market_paint_id", "warhammer-colour-contrast-apothecary-white")));
        repository.snapshot = new DataSnapshot(
                Map.of(), List.of(paint("warhammer-colour-contrast-apothecary-white", "Apothecary White"), alternative),
                List.of(Map.of("paint_id", "owned-alternative", "quantity", 1)), repository.snapshot.games(),
                List.of(guide), List.of(), List.of());
        var service = new MiniPaintDexService(repository, repository, repository, repository, repository);

        var result = service.reconcileMarketPaintingGuide("game-hero-guide");
        var slot = ((List<Map<String, Object>>) result.get("slots")).getFirst();
        var candidates = (List<Map<String, Object>>) slot.get("candidates");

        assertEquals(1, candidates.size());
        assertEquals("owned-alternative", ((Map<?, ?>) candidates.getFirst().get("paint")).get("id"));
        assertEquals(true, candidates.getFirst().get("requiresManualReview"));
    }

    @Test
    void createsValidatesActivatesAndAssignsAPersonalRecipe() {
        var repository = repository();
        repository.snapshot = new DataSnapshot(
                repository.snapshot.site(), repository.snapshot.marketPaints(),
                List.of(Map.of("paint_id", "warhammer-colour-contrast-apothecary-white", "quantity", 1)),
                repository.snapshot.games(), List.of(), List.of(), List.of(event("ws-1", "item-key")));
        var service = new MiniPaintDexService(repository, repository, repository, repository, repository);
        var solution = Map.<String, Object>of(
                "type", "single_paint", "paint_id", "warhammer-colour-contrast-apothecary-white");

        var created = service.createWorkshopRecipe(new CreateWorkshopRecipeCommand(
                "recipe-1", "game-hero", null, null, "My hero", 1, List.of(solution),
                "owner", null, "recipe-flow", "recipe-create"));
        service.transitionWorkshopRecipe(new TransitionWorkshopRecipeCommand(
                "recipe-1", "validate", null, "owner", null, "recipe-flow", "recipe-validate"));
        service.transitionWorkshopRecipe(new TransitionWorkshopRecipeCommand(
                "recipe-1", "activate", null, "owner", null, "recipe-flow", "recipe-activate"));
        var assigned = service.assignWorkshopRecipe(new AssignWorkshopRecipeCommand(
                "ws-1", "recipe-1", "owner", null, "recipe-flow", "recipe-assign"));

        assertEquals("workshop_recipe.created", created.eventType());
        assertEquals("recipe.assigned", assigned.eventType());
        assertEquals("recipe-1", service.listWorkshopItems("game").getFirst().get("recipeId"));
    }

    private static FakeRepository repository() {
        return new FakeRepository(snapshot(List.of()));
    }

    private static DataSnapshot snapshot(List<DomainEvent> events) {
        return new DataSnapshot(
                Map.of(),
                List.of(paint("warhammer-colour-contrast-apothecary-white", "Apothecary White")),
                List.of(),
                List.of(Map.of("id", "game", "catalog_items", List.of(Map.of("id", "game-hero")))),
                List.of(),
                List.of(),
                events);
    }

    private static Map<String, Object> paint(String id, String name) {
        return Map.ofEntries(
                Map.entry("id", id),
                Map.entry("brand", "Warhammer Colour"),
                Map.entry("brand_aliases", List.of("Citadel")),
                Map.entry("manufacturer", "Games Workshop"),
                Map.entry("range", "Contrast"),
                Map.entry("functional_type", "one_coat_contrast"),
                Map.entry("reference", "29-34"),
                Map.entry("name", name),
                Map.entry("color", Map.of("hex", "#D9DEDA", "family", "White")),
                Map.entry("finish", "matt"),
                Map.entry("medium", "water acrylic"),
                Map.entry("opacity", "transparent"),
                Map.entry("volume_ml", 18),
                Map.entry("lifecycle_status", "current"),
                Map.entry("tags", List.of("cold")));
    }

    private static DomainEvent event(String id, String idempotencyKey) {
        var instant = Instant.parse("2026-08-30T10:00:00Z");
        return new DomainEvent("01KTESTEVENT00000000000000", 1, "workshop_item.added", instant, instant,
                "workshop_item", id, "game", new Actor("user", "owner"), "correlation", null,
                idempotencyKey, Map.of("catalog_item_id", "game-hero", "display_name", "Hero"));
    }

    private static final class FakeRepository implements SnapshotRepository, EventLedger, MarketPaintCatalogWriter, WorkshopPaintInventoryWriter, ProjectCatalogWriter {
        private DataSnapshot snapshot;
        private final List<DomainEvent> appended = new ArrayList<>();
        private List<Map<String, Object>> replaced = List.of();
        private List<Map<String, Object>> inventory = List.of();
        private int projectsWritten;

        private FakeRepository(DataSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public DataSnapshot load() {
            return snapshot;
        }

        @Override
        public void append(DomainEvent event) {
            appended.add(event);
            var events = new ArrayList<>(snapshot.events());
            events.add(event);
            snapshot = new DataSnapshot(snapshot.site(), snapshot.marketPaints(), snapshot.paintInventory(),
                    snapshot.games(), snapshot.marketPaintingGuides(), snapshot.shopping(), List.copyOf(events));
        }

        @Override
        public void replaceMarketPaints(List<Map<String, Object>> paints) {
            replaced = List.copyOf(paints);
        }

        @Override
        public void replaceWorkshopPaints(List<Map<String, Object>> paints) {
            inventory = List.copyOf(paints);
        }

        @Override
        public void replaceProject(String projectId, Map<String, Object> project, List<Map<String, Object>> paintingGuides) {
            projectsWritten++;
        }
    }
}
