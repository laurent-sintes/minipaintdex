package com.minipaintdex.application;

import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.AddWorkshopItemCommentCommand;
import com.minipaintdex.application.command.AddWorkshopItemPhotoCommand;
import com.minipaintdex.application.command.ApplyMarketPaintChangeSetCommand;
import com.minipaintdex.application.command.ApplyMarketPaintableProductChangeSetCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.command.SetShoppingItemStatusCommand;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventLedger;
import com.minipaintdex.application.port.MarketPaintCatalogWriter;
import com.minipaintdex.application.port.PaintableProductCatalogWriter;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.WorkshopPaintInventoryWriter;
import com.minipaintdex.application.port.WorkshopMediaStorage;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.paint.PaintMatchEngine;
import com.minipaintdex.domain.paint.PaintMatchingPolicy;
import com.minipaintdex.domain.product.PaintableProduct;
import com.minipaintdex.domain.workflow.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniPaintDexServiceTest {
    @Test
    void searchesEverySupportedMarketFacet() {
        var result = service(repository()).searchMarketPaints(new SearchMarketPaintsQuery(
                "white", "Warhammer Colour", "Contrast", "one_coat_contrast", "White",
                "matt", "water acrylic", "transparent", "18", "29-34", "current",
                "Games Workshop", "cold"));
        assertEquals(1, result.size());
        assertEquals("Apothecary White", result.getFirst().get("name"));
    }

    @Test
    void rejectsAnItemOutsideTheImportedPaintableProductCatalog() {
        var repository = repository();
        repository.snapshot = snapshot(List.of(workshopImported()));
        var exception = assertThrows(DomainException.class, () -> service(repository).addWorkshopItem(
                new AddWorkshopItemCommand("ws-2", "unknown", "game", "Unknown", null, null, null, "key")));
        assertEquals("not_found", exception.code());
        assertTrue(repository.appended.isEmpty());
    }

    @Test
    void returnsTheExistingEventForAnIdempotentMutation() {
        var repository = repository();
        var existing = itemAdded("ws-1", "key");
        repository.snapshot = snapshot(List.of(workshopImported(), existing));
        var result = service(repository).addWorkshopItem(
                new AddWorkshopItemCommand("ws-1", "game-hero", "game", "Hero", null, null, null, "key"));
        assertEquals(existing, result);
        assertTrue(repository.appended.isEmpty());
    }

    @Test
    void previewsThenAppliesAMarketPaintChangeSet() {
        var repository = repository();
        var service = service(repository);
        var operation = new ApplyMarketPaintChangeSetCommand.Operation("upsert", paint("new-paint", "New Paint"), 2, false);
        var preview = service.applyMarketPaintChangeSet(
                new ApplyMarketPaintChangeSetCommand(1, "market_paints", List.of(operation), true));
        assertFalse(preview.applied());
        assertEquals(1, preview.added());
        assertTrue(repository.replaced.isEmpty());

        var applied = service.applyMarketPaintChangeSet(
                new ApplyMarketPaintChangeSetCommand(1, "market_paints", List.of(operation), false));
        assertTrue(applied.applied());
        assertEquals(2, repository.inventory.getFirst().get("quantity"));
    }

    @Test
    void validatesAMarketPaintableProductWithoutImportingIt() {
        var repository = repository();
        var result = service(repository).applyMarketPaintableProductChangeSet(
                new ApplyMarketPaintableProductChangeSetCommand(
                        1, "market_product", productMap("new-product"), List.of(), true, "owner", "catalog-import"));
        assertFalse(result.applied());
        assertEquals(1, result.catalogItems());
        assertEquals(0, repository.productsWritten);
        assertTrue(repository.appended.isEmpty());
    }

    @Test
    void createsAPaintingProjectAsOneAtomicWorkshopBatch() {
        var repository = repository();
        var service = service(repository);
        var preview = service.previewProductImport("game");
        assertEquals(false, preview.get("alreadyImported"));
        assertEquals(1, preview.get("paintableItemCount"));

        var imported = service.createPaintingProject(new CreatePaintingProjectCommand(
                "game", "paint-game", "Paint Game", "owner",
                Instant.parse("2026-08-30T10:00:00Z"), "import", "import-game"));
        assertTrue(imported.applied());
        assertEquals(1, imported.workshopItemsAdded());
        assertEquals(1, repository.batches);
        assertEquals(List.of("workshop.created", "painting_project.created", "workshop_item.added"),
                repository.appended.stream().map(DomainEvent::eventType).toList());

        var repeated = service.createPaintingProject(new CreatePaintingProjectCommand(
                "game", "another-project", "Duplicate", "owner", null, null, "another-key"));
        assertTrue(repeated.alreadyExists());
        assertFalse(repeated.applied());
        assertEquals(1, repository.batches);
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
                List.of(Map.of("paint_id", "owned-alternative", "quantity", 1)), List.of(product()),
                List.of(guide), List.of(), List.of());
        var result = service(repository).reconcileMarketPaintingGuide("game-hero-guide");
        @SuppressWarnings("unchecked") var slots = (List<Map<String, Object>>) result.get("slots");
        @SuppressWarnings("unchecked") var candidates = (List<Map<String, Object>>) slots.getFirst().get("candidates");
        assertEquals("owned-alternative", ((Map<?, ?>) candidates.getFirst().get("paint")).get("id"));
        assertEquals(true, candidates.getFirst().get("requiresManualReview"));
    }

    @Test
    void createsValidatesActivatesAndAssignsAPersonalRecipe() {
        var repository = repository();
        repository.snapshot = new DataSnapshot(
                Map.of(), repository.snapshot.marketPaints(),
                List.of(Map.of("paint_id", "warhammer-colour-contrast-apothecary-white", "quantity", 1)),
                List.of(product()), List.of(), List.of(), List.of(workshopImported(), itemAdded("ws-1", "item-key")));
        var service = service(repository);
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

    @Test
    void preservesDottedDomainIdentifiersWhileCamelizingSiteConfiguration() {
        var repository = repository();
        repository.snapshot = new DataSnapshot(
                Map.of("workshop", Map.of("event_labels", Map.of("workshop_item.added", "Élément ajouté"))),
                repository.snapshot.marketPaints(), repository.snapshot.paintInventory(),
                repository.snapshot.paintableProducts(), repository.snapshot.marketPaintingGuides(),
                repository.snapshot.shopping(), repository.snapshot.events());

        var bootstrap = service(repository).bootstrap();
        var config = map(bootstrap.get("config"));
        var workshop = map(config.get("workshop"));
        var eventLabels = map(workshop.get("eventLabels"));

        assertEquals("Élément ajouté", eventLabels.get("workshop_item.added"));
    }

    @Test
    void recordsCommentsAndPhotosOnThePhysicalWorkshopItem() {
        var repository = repository();
        repository.snapshot = snapshot(List.of(workshopImported(), itemAdded("ws-1", "item-key")));
        var service = service(repository);

        service.addWorkshopItemComment(new AddWorkshopItemCommentCommand(
                "ws-1", "Ready for priming", "owner", null, "journal", "comment-1"));
        service.addWorkshopItemPhoto(new AddWorkshopItemPhotoCommand(
                "ws-1", "progress.png", "image/png", new byte[]{1, 2, 3}, "preparation", "Cleaned",
                "owner", null, "journal", "photo-1"));

        var detail = service.getWorkshopItem("ws-1");
        @SuppressWarnings("unchecked") var activity = (List<DomainEvent>) detail.get("activity");
        assertEquals(List.of("workshop_item.photo_added", "workshop_item.comment_added", "workshop_item.added"),
                activity.stream().map(DomainEvent::eventType).toList());
        assertEquals(1, repository.mediaStored);
    }

    @Test
    void persistsShoppingCompletionInTheLedgerProjection() {
        var repository = repository();
        repository.snapshot = new DataSnapshot(
                Map.of(), repository.snapshot.marketPaints(), List.of(), List.of(product()), List.of(),
                List.of(Map.of("id", "buy-1", "market_paint_id", "warhammer-colour-contrast-apothecary-white",
                        "reason", "Plan", "priority", "high")), List.of());
        var service = service(repository);

        service.setShoppingItemStatus(new SetShoppingItemStatusCommand("buy-1", true, "owner", null, "shopping", "buy-1-done"));

        @SuppressWarnings("unchecked") var shopping = (List<Map<String, Object>>) service.bootstrap(false).get("shoppingSeed");
        assertEquals(true, shopping.getFirst().get("checked"));
    }

    @Test
    void paginatesMarketPaintSearches() {
        var repository = repository();
        repository.snapshot = new DataSnapshot(Map.of(), List.of(paint("paint-1", "A"), paint("paint-2", "B")),
                List.of(), List.of(product()), List.of(), List.of(), List.of());

        var page = service(repository).searchMarketPaintPage(SearchMarketPaintsQuery.empty(), false, false, false, 1, 1);

        assertEquals(2, page.get("total"));
        assertEquals(1, ((List<?>) page.get("paints")).size());
    }

    private static FakeRepository repository() { return new FakeRepository(snapshot(List.of())); }

    private static MiniPaintDexService service(FakeRepository repository) {
        var policy = new PaintMatchingPolicy(
                5, Set.of("one_coat_contrast", "technical_effect", "primer", "wash_shade", "ink", "auxiliary"),
                2.5, 20, 25, 50, 50, 80, 75,
                new PaintMatchingPolicy.Weights(.65, .15, 0, .08, .07, .05),
                new PaintMatchingPolicy.Weights(.15, .35, .30, .10, .10, 0));
        return new MiniPaintDexService(repository, repository, repository, repository, repository, repository,
                new WorkshopMediaPolicy(10 * 1024 * 1024, Set.of("image/jpeg", "image/png", "image/webp")),
                new PaintMatchEngine(policy));
    }

    private static DataSnapshot snapshot(List<DomainEvent> events) {
        return new DataSnapshot(Map.of(),
                List.of(paint("warhammer-colour-contrast-apothecary-white", "Apothecary White")),
                List.of(), List.of(product()), List.of(), List.of(), events);
    }

    private static PaintableProduct product() {
        return new PaintableProduct(1, "game", "Game", "Game line", "board_game", "full set", 1,
                new PaintableProduct.Edition("", ""), List.of(),
                List.of(new PaintableProduct.CatalogItem(
                        "game-hero", "game", "Hero", "hero", 1, "", false, List.of(), List.of())));
    }

    private static Map<String, Object> productMap(String id) {
        return Map.ofEntries(
                Map.entry("schema_version", 1), Map.entry("id", id), Map.entry("name", "New Product"),
                Map.entry("line", "New line"), Map.entry("product_type", "board_game"),
                Map.entry("scope", "full set"), Map.entry("expected_paintable_count", 1),
                Map.entry("edition", Map.of()), Map.entry("sources", List.of()),
                Map.entry("catalog_items", List.of(Map.of(
                        "id", id + "-hero", "product_id", id, "name", "Hero", "kind", "hero", "quantity", 1))));
    }

    private static Map<String, Object> paint(String id, String name) {
        return Map.ofEntries(
                Map.entry("id", id), Map.entry("brand", "Warhammer Colour"), Map.entry("brand_aliases", List.of("Citadel")),
                Map.entry("manufacturer", "Games Workshop"), Map.entry("range", "Contrast"),
                Map.entry("functional_type", "one_coat_contrast"), Map.entry("reference", "29-34"), Map.entry("name", name),
                Map.entry("color", Map.of("hex", "#D9DEDA", "family", "White")), Map.entry("finish", "matt"),
                Map.entry("medium", "water acrylic"), Map.entry("opacity", "transparent"), Map.entry("volume_ml", 18),
                Map.entry("lifecycle_status", "current"), Map.entry("tags", List.of("cold")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static DomainEvent workshopImported() {
        var instant = Instant.parse("2026-08-30T10:00:00Z");
        return new DomainEvent("01KTESTWORKSHOP000000000000", 1, "painting_project.created", instant, instant,
                "painting_project", "game", "game", new Actor("user", "owner"), "correlation", null,
                "painting-project", Map.of("workshop_id", "my-workshop", "paintable_product_id", "game", "name", "Paint Game"));
    }

    private static DomainEvent itemAdded(String id, String idempotencyKey) {
        var instant = Instant.parse("2026-08-30T10:00:01Z");
        return new DomainEvent("01KTESTEVENT00000000000000", 1, "workshop_item.added", instant, instant,
                "workshop_item", id, null, new Actor("user", "owner"), "correlation", null,
                idempotencyKey, Map.of("catalog_item_id", "game-hero", "painting_project_id", "game", "display_name", "Hero"));
    }

    private static final class FakeRepository implements SnapshotRepository, EventLedger, MarketPaintCatalogWriter,
            WorkshopPaintInventoryWriter, PaintableProductCatalogWriter, WorkshopMediaStorage {
        private DataSnapshot snapshot;
        private final List<DomainEvent> appended = new ArrayList<>();
        private List<Map<String, Object>> replaced = List.of();
        private List<Map<String, Object>> inventory = List.of();
        private int productsWritten;
        private int batches;
        private int mediaStored;

        private FakeRepository(DataSnapshot snapshot) { this.snapshot = snapshot; }
        public DataSnapshot load() { return snapshot; }
        public List<DomainEvent> appendAll(List<DomainEvent> events) {
            batches++;
            var existingByKey = snapshot.events().stream().filter(event -> event.idempotencyKey() != null)
                    .collect(java.util.stream.Collectors.toMap(DomainEvent::idempotencyKey, event -> event, (left, right) -> left));
            if (events.stream().allMatch(event -> event.idempotencyKey() != null && existingByKey.containsKey(event.idempotencyKey()))) {
                return events.stream().map(event -> existingByKey.get(event.idempotencyKey())).toList();
            }
            appended.addAll(events);
            var all = new ArrayList<>(snapshot.events());
            all.addAll(events);
            snapshot = new DataSnapshot(snapshot.site(), snapshot.marketPaints(), snapshot.paintInventory(),
                    snapshot.paintableProducts(), snapshot.marketPaintingGuides(), snapshot.shopping(), List.copyOf(all));
            return List.copyOf(events);
        }
        public void replaceMarketPaints(List<Map<String, Object>> paints) { replaced = List.copyOf(paints); }
        public void replaceWorkshopPaints(List<Map<String, Object>> paints) { inventory = List.copyOf(paints); }
        public void replaceProduct(String productId, Map<String, Object> product, List<Map<String, Object>> paintingGuides) { productsWritten++; }
        public StoredMedia store(String itemId, String mediaId, String originalFilename, String contentType, byte[] content) {
            mediaStored++;
            return new StoredMedia(mediaId, "/media/workshop/" + itemId + "/" + mediaId + ".jpg", "workshop/" + itemId + "/" + mediaId + ".jpg", originalFilename, contentType, content.length, "hash");
        }
        public void delete(StoredMedia media) { }
    }
}
