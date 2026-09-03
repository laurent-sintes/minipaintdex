package com.minipaintdex.application;

import com.minipaintdex.application.validation.StructuredDocuments;

import com.minipaintdex.application.command.*;
import com.minipaintdex.application.command.AddWorkshopPaintableCommentCommand;
import com.minipaintdex.application.command.AddWorkshopPaintablePhotoCommand;
import com.minipaintdex.application.command.ApplyPaintProductChangeSetCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.SetShoppingListEntryCheckedCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.command.TransitionPaintingProjectCommand;
import com.minipaintdex.application.event.EventBatch;
import com.minipaintdex.application.event.EventBusState;
import com.minipaintdex.application.event.EventPublication;
import com.minipaintdex.application.event.EventPublicationStatus;
import com.minipaintdex.application.event.PublicationReceipt;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SortOrder;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventBus;
import com.minipaintdex.application.port.PaintProductCatalogWriter;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.port.PaintableProductCatalogWriter;
import com.minipaintdex.application.port.PersistenceLifecycle;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.WorkshopMediaStorage;
import com.minipaintdex.application.query.SearchPaintProductsQuery;
import com.minipaintdex.application.validation.MarketCatalogFactory;
import com.minipaintdex.application.view.PaintProductView;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.market.paint.PaintMatchEngine;
import com.minipaintdex.domain.market.paint.PaintMatchingPolicy;
import com.minipaintdex.domain.market.product.PaintableProduct;
import com.minipaintdex.domain.shared.DomainException;
import com.minipaintdex.domain.workshop.PaintingProjectCreated;
import com.minipaintdex.domain.workshop.PaintingProjectRegistered;
import com.minipaintdex.domain.workshop.PaintingProjectStatus;
import com.minipaintdex.domain.workshop.PaintingProjectStatusChanged;
import com.minipaintdex.domain.workshop.RecipeSolution;
import com.minipaintdex.domain.workshop.RecipeSolutionType;
import com.minipaintdex.domain.workshop.ShoppingListEntryCheckedChanged;
import com.minipaintdex.domain.workshop.WorkshopCreated;
import com.minipaintdex.domain.workshop.WorkshopPaintableAdded;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationServicesTest {
    private static final Instant AT = Instant.parse("2026-08-30T10:00:00Z");

    @Test
    void searchesEverySupportedMarketFacet() {
        var result = marketService(repository()).searchPaintProducts(SearchPaintProductsQuery.fromSelections(
                "white", null, List.of("Warhammer Colour::Contrast"), List.of("color_paint"), List.of("brush"),
                List.of("one_coat_shading"), List.of("White"), List.of("matte"), List.of("water_based_acrylic"), List.of("transparent"),
                null, List.of("light"), List.of("active")));
        assertEquals(1, result.size());
        assertEquals("Apothecary White", result.getFirst().name());
    }

    @Test
    void treatsAuxiliaryAsAFilterableSpecialToneWithoutRequiringAHexColor() {
        var chromaticWithoutHex = new LinkedHashMap<>(paint("paint-red", "Red"));
        chromaticWithoutHex.put("color", Map.of("hex", "", "family", "red"));
        var repository = repository();
        repository.snapshot = snapshot(
                document(Map.of()), documents(List.of(chromaticWithoutHex, auxiliaryPaint())),
                List.of(), List.of(product()), List.of(), List.of(), List.of(), List.of());
        var market = marketService(repository);

        assertEquals(1, market.paintProductQuality().missingColorHex());
        var filtered = market.searchPaintProducts(new SearchPaintProductsQuery(
                null, null, null, null, null, null, List.of("auxiliary"),
                null, null, null, null, null, null));
        assertEquals(List.of("paint-auxiliary"), filtered.stream().map(PaintProductView::id).toList());
    }

    @Test
    void workshopFacetsCountOnlyOwnedReferencesButKeepAlternativesSelectable() {
        var blue = new LinkedHashMap<>(paint("paint-blue", "Blue"));
        blue.put("color", Map.of("hex", "#0000FF", "family", "blue"));
        var red = new LinkedHashMap<>(paint("paint-red", "Red"));
        red.put("color", Map.of("hex", "#FF0000", "family", "red"));
        var repository = repository();
        repository.snapshot = snapshot(document(Map.of()), documents(List.of(blue, red, auxiliaryPaint())),
                documents(List.of(Map.of("paint_id", "paint-blue", "quantity", 2), Map.of("paint_id", "paint-red", "quantity", 1))),
                List.of(product()), List.of(), List.of(), List.of(), List.of());
        var filters = SearchPaintProductsQuery.fromSelections("", null, null, null, null, null, List.of("blue"), null, null, null, null, null, null);
        var facets = service(repository).workshopPaintStockFacets(filters, false, false);
        assertEquals(1, facets.total());
        var colors = facets.facets().stream().filter(facet -> facet.id().equals("colors")).findFirst().orElseThrow().values();
        assertEquals(List.of("blue", "red"), colors.stream().map(value -> value.value()).toList());
        assertEquals(List.of(1, 1), colors.stream().map(value -> value.count()).toList());
        assertEquals(3, marketService(repository).paintProductFacets(SearchPaintProductsQuery.empty(), false, false).total());
    }

    @Test
    void suggestionsLimitAfterOwnershipAndRetainIndependentReferences() {
        var repository = repository();
        repository.snapshot = snapshot(document(Map.of()),
                documents(List.of(paint("a", "White A"), paint("b", "White B"), paint("c", "White C"))),
                documents(List.of(Map.of("paint_id", "b", "quantity", 2), Map.of("paint_id", "c", "quantity", 1))),
                List.of(product()), List.of(), List.of(), List.of(), List.of());
        var filters = SearchPaintProductsQuery.fromSelections("white", null, null, null, null, null, null, null, null, null, null, null, null);
        var query = new com.minipaintdex.application.query.PaintSearchQuery(filters, false, false, java.util.Set.of("suggestions"), null, 1, "suggest-test");
        var before = repository.snapshot;
        assertEquals("a", marketService(repository).searchPaintProducts(query).suggestions().getFirst().paintProductId());
        var workshop = service(repository).searchWorkshopPaintStocks(query);
        assertEquals(List.of("b"), workshop.suggestions().stream().map(p -> p.paintProductId()).toList());
        assertEquals("suggest-test", workshop.correlationId());
        assertEquals(before, repository.snapshot);
        assertTrue(repository.batches.isEmpty());
        assertThrows(DomainException.class, () -> service(repository).searchWorkshopPaintStocks(
                new com.minipaintdex.application.query.PaintSearchQuery(filters, false, false, java.util.Set.of("suggestions"), null, 21, "test")));
        assertTrue(service(repository).searchWorkshopPaintStocks(
                new com.minipaintdex.application.query.PaintSearchQuery(SearchPaintProductsQuery.empty(), false, false, java.util.Set.of("suggestions"), null, null, "test")).suggestions().isEmpty());
    }

    @Test
    void combinedSearchKeepsSuggestionsIndependentOfPageSortAndWorkshopQuantities() {
        var repository = repository();
        repository.snapshot = snapshot(document(Map.of()),
                documents(List.of(paint("a", "White A"), paint("b", "White B"), paint("c", "White C"))),
                documents(List.of(Map.of("paint_id", "b", "quantity", 2), Map.of("paint_id", "c", "quantity", 1))),
                List.of(product()), List.of(), List.of(), List.of(), List.of());
        var filters = SearchPaintProductsQuery.fromSelections("white", null, null, null, null, null, null, null, null, null, null, null, null);
        var query = new com.minipaintdex.application.query.PaintSearchQuery(filters, false, false,
                Set.of("results", "suggestions"), new PageQuery(0, 1, List.of(new SortOrder("name", SortOrder.Direction.DESCENDING))), 1, "both");
        var market = marketService(repository).searchPaintProducts(query);
        assertEquals("c", market.results().content().getFirst().id());
        assertEquals(3, market.results().totalElements());
        assertEquals("a", market.suggestions().getFirst().paintProductId());
        var workshop = service(repository).searchWorkshopPaintStocks(query);
        assertEquals("c", workshop.results().content().getFirst().paintProduct().id());
        assertEquals(2, workshop.results().totalElements());
        assertEquals("b", workshop.suggestions().getFirst().paintProductId());
        assertTrue(repository.batches.isEmpty());
        var pageOnly = service(repository).searchWorkshopPaintStocks(new com.minipaintdex.application.query.PaintSearchQuery(
                filters, false, false, null, new PageQuery(1, 1, List.of()), null, "page"));
        assertEquals(1, pageOnly.results().page());
        assertEquals(null, pageOnly.suggestions());
        assertThrows(DomainException.class, () -> new com.minipaintdex.application.query.PaintSearchQuery(
                filters, false, false, new java.util.HashSet<>(java.util.Arrays.asList("results", null)), query.page(), null, "bad"));
    }

    @Test
    void createsACompletePaintingProjectAsOneAtomicPublication() {
        var repository = repository();
        var result = service(repository).createPaintingProject(new CreatePaintingProjectCommand(
                "game", "paint-game", "Paint Game", "owner", AT, "import", "import-game"));

        assertTrue(result.applied());
        assertEquals(1, result.workshopPaintablesAdded());
        assertEquals(1, repository.batches.size());
        assertEquals(List.of(
                        "workshop.created", "painting_project.created", "painting_project.status_changed",
                        "workshop.painting_project_registered", "workshop_item.added"),
                repository.batches.getFirst().events().stream().map(EventEnvelope::eventType).toList());
        assertEquals(PaintingProjectStatus.ACTIVE.id(),
                service(repository).listPaintingProjects().getFirst().status());
    }

    @Test
    void changesPaintingProjectLifecycleThroughItsAggregate() {
        var repository = repositoryWithImportedItem();
        var service = service(repository);

        service.transitionPaintingProject(new TransitionPaintingProjectCommand(
                "paint-game", "completed", "owner", AT, "project-lifecycle", "project-complete"));

        assertEquals(PaintingProjectStatus.COMPLETED.id(),
                service.listPaintingProjects().getFirst().status());
        assertEquals("painting_project.status_changed",
                repository.batches.getFirst().events().getFirst().eventType());
    }

    @Test
    void createsValidatesActivatesAndAssignsATypedRecipe() {
        var repository = repositoryWithImportedItem();
        repository.snapshot = snapshot(
                document(Map.of()), repository.snapshot.paintProducts(),
                documents(List.of(Map.of("paint_id", "warhammer-colour-contrast-apothecary-white", "quantity", 1))),
                repository.snapshot.paintableProducts(), List.of(), List.of(), repository.snapshot.events(), List.of());
        var service = service(repository);
        var solution = new RecipeSolution(
                RecipeSolutionType.SINGLE_PAINT, null,
                "warhammer-colour-contrast-apothecary-white", List.of(), null);

        service.createWorkshopRecipe(new CreateWorkshopRecipeCommand(
                "recipe-1", "game-hero", null, null, "My hero", 1, List.of(solution),
                "owner", AT, "recipe-flow", "recipe-create"));
        service.transitionWorkshopRecipe(new TransitionWorkshopRecipeCommand(
                "recipe-1", "validate", null, null, "owner", AT, "recipe-flow", "recipe-validate"));
        service.transitionWorkshopRecipe(new TransitionWorkshopRecipeCommand(
                "recipe-1", "activate", null, null, "owner", AT, "recipe-flow", "recipe-activate"));
        service.assignWorkshopRecipe(new AssignWorkshopRecipeCommand(
                "ws-game-hero-001", "recipe-1", "owner", AT, "recipe-flow", "recipe-assign"));

        assertEquals("recipe-1", service.listWorkshopPaintables("paint-game").getFirst().recipeId());
    }

    @Test
    void previewsThenAppliesAPaintProductChangeSet() {
        var repository = repository();
        var service = new AdministrationApplicationService(repository, repository, repository);
        var operation = new ApplyPaintProductChangeSetCommand.Operation(
                "upsert", null, document(paint("new-paint", "New Paint")), 0, false);
        var preview = service.applyPaintProductChangeSet(
                new ApplyPaintProductChangeSetCommand(1, "market_paints", List.of(operation), true, List.of(), List.of()));
        assertFalse(preview.applied());
        assertTrue(repository.replaced.isEmpty());

        var applied = service.applyPaintProductChangeSet(
                new ApplyPaintProductChangeSetCommand(1, "market_paints", List.of(operation), false, List.of(), List.of()));
        assertTrue(applied.applied());
        assertTrue(repository.replaced.stream().anyMatch(value -> "new-paint".equals(documentMap(value).get("id"))));
        assertTrue(repository.inventory.isEmpty());
    }

    @Test
    void rejectsAmbiguousPaintChangeSetsBeforeAnyWrite() {
        var repository = repository();
        var service = new AdministrationApplicationService(repository, repository, repository);
        var operation = new ApplyPaintProductChangeSetCommand.Operation(
                "upsert", null, document(paint("new-paint", "New Paint")), 0, false);

        assertThrows(DomainException.class, () -> service.applyPaintProductChangeSet(
                new ApplyPaintProductChangeSetCommand(
                        1, "market_paints", List.of(operation, operation), false, List.of(), List.of())));
        assertTrue(repository.replaced.isEmpty());
    }

    @Test
    void rekeysPaintAndMutableReferencesAsOneGeneration() {
        var repository = repository();
        var oldId = "warhammer-colour-contrast-apothecary-white";
        var newId = "cit-29-34";
        var guide = Map.<String, Object>of(
                "id", "guide", "version", 1, "knowledge_status", "documented",
                "catalog_item_id", "game-hero", "source_refs", List.of("source"),
                "slots", List.of(Map.of("id", "base", "role", "Base coat", "market_paint_id", oldId)));
        repository.snapshot = snapshot(
                document(Map.of()), repository.snapshot.paintProducts(),
                List.of(),
                repository.snapshot.paintableProducts(), documents(List.of(guide)),
                documents(List.of(Map.of("id", "buy", "market_paint_id", oldId, "reason", "Need", "priority", "high"))),
                List.of(), List.of());
        var migrated = new LinkedHashMap<>(paint(newId, "Apothecary White"));
        var operation = new ApplyPaintProductChangeSetCommand.Operation(
                "rekey", oldId, document(migrated), 0, false);
        var service = new AdministrationApplicationService(repository, repository, repository);

        var result = service.applyPaintProductChangeSet(
                new ApplyPaintProductChangeSetCommand(1, "market_paints", List.of(operation), false, List.of(), List.of()));

        assertEquals(1, result.rekeyed());
        assertEquals(newId, documentMap(repository.replaced.getFirst()).get("id"));
        var migratedSlot = (Map<?, ?>) ((List<?>) documentMap(repository.guides.getFirst()).get("slots")).getFirst();
        assertEquals(newId, migratedSlot.get("market_paint_id"));
        assertEquals(newId, documentMap(repository.shopping.getFirst()).get("market_paint_id"));
    }

    @Test
    void recordsCommentsAndPhotosOnThePhysicalItem() {
        var repository = repositoryWithImportedItem();
        var service = service(repository);
        service.addWorkshopPaintableComment(new AddWorkshopPaintableCommentCommand(
                "ws-game-hero-001", "Ready", "owner", AT, "journal", "comment-1"));
        service.addWorkshopPaintablePhoto(new AddWorkshopPaintablePhotoCommand(
                "ws-game-hero-001", "progress.png", "image/png", new byte[]{1, 2, 3},
                "preparation", "Cleaned", "owner", AT, "journal", "photo-1"));

        var activity = service.getWorkshopPaintable("ws-game-hero-001").activity();
        assertEquals(List.of("workshop_item.photo_added", "workshop_item.comment_added", "workshop_item.added"),
                activity.stream().map(EventEnvelope::eventType).limit(3).toList());
        assertEquals(1, repository.mediaStored);
    }

    @Test
    void serializesConcurrentCommandsOnTheEffectiveAggregateState() throws Exception {
        var repository = repositoryWithImportedItem();
        var service = service(repository);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.addWorkshopPaintableComment(new AddWorkshopPaintableCommentCommand(
                    "ws-game-hero-001", "First", "owner", AT, "concurrent", "comment-a")));
            var second = executor.submit(() -> service.addWorkshopPaintableComment(new AddWorkshopPaintableCommentCommand(
                    "ws-game-hero-001", "Second", "owner", AT, "concurrent", "comment-b")));
            first.get();
            second.get();
        }

        var versions = repository.batches.stream()
                .flatMap(batch -> batch.events().stream())
                .map(EventEnvelope::aggregateVersion).sorted().toList();
        assertEquals(List.of(2L, 3L), versions);
    }

    @Test
    void persistsShoppingCompletionThroughTheEventBus() {
        var repository = repository();
        repository.snapshot = snapshot(
                document(Map.of()), repository.snapshot.paintProducts(), List.of(), List.of(product()), List.of(),
                documents(List.of(Map.of("id", "buy-1", "market_paint_id", "warhammer-colour-contrast-apothecary-white"))),
                List.of(), List.of());
        var service = service(repository);
        service.setShoppingListEntryChecked(new SetShoppingListEntryCheckedCommand(
                "buy-1", true, "owner", AT, "shopping", "buy-1-done"));

        assertEquals(true, service.listShoppingListEntries().getFirst().checked());
    }

    @Test
    void shoppingCheckedProjectionUsesLedgerSequenceWhenTimestampsTie() {
        var repository = repository();
        var checked = new EventEnvelope("zz-checked", 1, 1, AT, new Actor("user", "owner"),
                "shopping", null, "check", new ShoppingListEntryCheckedChanged("buy-1", true, AT));
        var unchecked = new EventEnvelope("aa-unchecked", 1, 2, AT, new Actor("user", "owner"),
                "shopping", null, "uncheck", new ShoppingListEntryCheckedChanged("buy-1", false, AT));
        repository.snapshot = snapshot(
                document(Map.of()), repository.snapshot.paintProducts(), List.of(), List.of(product()), List.of(),
                documents(List.of(Map.of("id", "buy-1", "market_paint_id", "warhammer-colour-contrast-apothecary-white"))),
                List.of(checked, unchecked), List.of());

        assertFalse(service(repository).listShoppingListEntries().getFirst().checked());
    }

    @Test
    void paginatesPaintProductSearches() {
        var repository = repository();
        repository.snapshot = snapshot(
                document(Map.of()), documents(List.of(paint("paint-1", "A"), paint("paint-2", "B"))),
                List.of(), List.of(product()), List.of(), List.of(), List.of(), List.of());
        var page = marketService(repository).searchPaintProducts(new com.minipaintdex.application.query.PaintSearchQuery(SearchPaintProductsQuery.empty(), false, false, java.util.Set.of("results"), new PageQuery(1, 1, List.of()), null, "test")).results();
        assertEquals(2, page.totalElements());
        assertEquals(1, page.content().size());
    }

    @Test
    void realResultFilterRequiresAnActualImageAndKeepsFacetsConsistent() {
        var linkedOnly = new LinkedHashMap<>(paint("paint-linked", "Linked only"));
        linkedOnly.put("result_image", Map.of("reference_url", "https://example.test/result"));
        var illustrated = new LinkedHashMap<>(paint("paint-illustrated", "Illustrated"));
        illustrated.put("result_image", Map.of(
                "source_url", "https://example.test/result.webp",
                "reference_url", "https://example.test/result"));
        var repository = repository();
        repository.snapshot = snapshot(
                document(Map.of()), documents(List.of(linkedOnly, illustrated)),
                List.of(), List.of(product()), List.of(), List.of(), List.of(), List.of());
        var market = marketService(repository);

        var page = market.searchPaintProducts(new com.minipaintdex.application.query.PaintSearchQuery(SearchPaintProductsQuery.empty(), false, true, java.util.Set.of("results"), new PageQuery(0, 10, List.of()), null, "test")).results();
        var facets = market.paintProductFacets(SearchPaintProductsQuery.empty(), false, true);

        assertEquals(List.of("paint-illustrated"), page.content().stream().map(PaintProductView::id).toList());
        assertEquals(1, facets.total());
    }

    @Test
    void publishesAndAppliesTheConfiguredVerificationDateSort() {
        var older = new LinkedHashMap<>(paint("paint-older", "Older"));
        older.put("verified_at", "2025-01-01");
        var newer = new LinkedHashMap<>(paint("paint-newer", "Newer"));
        newer.put("verified_at", "2026-09-01");
        var repository = repository();
        repository.snapshot = snapshot(
                document(Map.of()), documents(List.of(older, newer)),
                List.of(), List.of(product()), List.of(), List.of(), List.of(), List.of());
        var market = marketService(repository);

        var page = market.searchPaintProducts(new com.minipaintdex.application.query.PaintSearchQuery(SearchPaintProductsQuery.empty(), false, false, java.util.Set.of("results"), new PageQuery(0, 10, List.of(new SortOrder("verifiedAt", SortOrder.Direction.DESCENDING))), null, "test")).results();

        assertEquals(List.of("paint-newer", "paint-older"), page.content().stream().map(PaintProductView::id).toList());
        assertTrue(market.paintProductModel().sortOptions().stream()
                .anyMatch(option -> "verifiedAt,desc".equals(option.queryValue())));
    }

    private static FakeRepository repository() {
        return new FakeRepository(snapshot(
                document(Map.of()), documents(List.of(paint("warhammer-colour-contrast-apothecary-white", "Apothecary White"))),
                List.of(), List.of(product()), List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    void appliesEditionsIdempotentlyWithoutChangingInventoryAndExposesBoundedQueries() {
        var repository = repository();
        var before = repository.snapshot.paintInventory();
        var edition = document(Map.of("schema_version", 1, "id", "catalog-2019", "brand", "Warhammer Colour",
                "title", "Catalogue", "edition_label", "2019", "publication_year", 2019,
                "ranges", List.of("Contrast"), "source_urls", List.of("https://example.com/catalog.pdf")));
        var administration = new AdministrationApplicationService(repository, repository, repository);
        administration.applyPaintProductChangeSet(new ApplyPaintProductChangeSetCommand(1, "market_paints", List.of(), true, List.of(edition), List.of()));
        assertTrue(repository.snapshot.paintCatalogEditions().isEmpty());
        var command = new ApplyPaintProductChangeSetCommand(1, "market_paints", List.of(), false, List.of(edition), List.of());
        administration.applyPaintProductChangeSet(command);
        administration.applyPaintProductChangeSet(command);
        assertEquals(List.of(edition), repository.snapshot.paintCatalogEditions());
        assertEquals(before, repository.snapshot.paintInventory());
        var market = marketService(repository);
        var page = market.searchPaintCatalogEditions(new com.minipaintdex.application.query.SearchPaintCatalogEditionsQuery(
                "Warhammer Colour", new PageQuery(0, 1, List.of()), "test"));
        assertEquals(1, page.totalElements());
        assertEquals("catalog-2019", page.content().getFirst().id());
        assertEquals(2019, market.getPaintCatalogEdition(new com.minipaintdex.application.query.GetPaintCatalogEditionQuery("catalog-2019", "test")).publicationYear());
        assertThrows(DomainException.class, () -> market.getPaintCatalogEdition(new com.minipaintdex.application.query.GetPaintCatalogEditionQuery("unknown", "test")));
    }

    @Test
    void importsUsageGuidesIdempotentlyAndQueriesTranslationsWithoutWorkshopWrites() {
        var repository = repository();
        var beforeEvents = repository.snapshot.events();
        var raw = new LinkedHashMap<String, Object>();
        raw.put("schema_version", 1); raw.put("id", "shared-usage"); raw.put("brand", "Warhammer Colour");
        raw.put("title", "Contrast"); raw.put("revision", 1); raw.put("ranges", List.of("Contrast"));
        raw.put("original_language", "en"); raw.put("knowledge_status", "generic-template"); raw.put("review_required", true);
        raw.put("original", Map.of("summary", "Usage", "steps", List.of("Shake"), "tips", List.of("Care")));
        raw.put("translations", List.of(Map.of("language", "fr", "source_revision", 1, "method", "machine", "review_required", true,
                "content", Map.of("summary", "Utilisation", "steps", List.of("Agiter"), "tips", List.of("Prudence")))));
        var guide = document(raw);
        var administration = new AdministrationApplicationService(repository, repository, repository);
        administration.applyPaintProductChangeSet(new ApplyPaintProductChangeSetCommand(1, "market_paints", List.of(), true, List.of(), List.of(guide)));
        assertTrue(repository.snapshot.paintUsageGuides().isEmpty());
        var command = new ApplyPaintProductChangeSetCommand(1, "market_paints", List.of(), false, List.of(), List.of(guide));
        administration.applyPaintProductChangeSet(command); administration.applyPaintProductChangeSet(command);
        assertEquals(List.of(guide), repository.snapshot.paintUsageGuides());
        assertEquals(beforeEvents, repository.snapshot.events());
        var market = marketService(repository);
        var query = new com.minipaintdex.application.query.SearchPaintUsageGuidesQuery("Warhammer Colour", "Contrast", null, "fr", new PageQuery(0, 1, List.of()), "test");
        var found = market.searchPaintUsageGuides(query);
        assertEquals(1, found.page().totalElements()); assertEquals("test", found.correlationId());
        assertEquals(List.of("Agiter"), found.page().content().getFirst().content().steps());
        var noAssignment = market.searchPaintUsageGuides(new com.minipaintdex.application.query.SearchPaintUsageGuidesQuery(null, null,
                repository.snapshot.paintProducts().stream().findFirst().map(d -> StructuredDocuments.toMap(d).get("id").toString()).orElseThrow(),
                "fr", new PageQuery(0, 10, List.of()), "test"));
        assertEquals(0, noAssignment.page().totalElements());
        assertEquals("en", market.getPaintUsageGuide(new com.minipaintdex.application.query.GetPaintUsageGuideQuery("shared-usage", "original", "test")).guide().language());
        raw.put("original", Map.of("summary", "Changed", "steps", List.of("Shake"), "tips", List.of("Care")));
        assertThrows(DomainException.class, () -> administration.applyPaintProductChangeSet(new ApplyPaintProductChangeSetCommand(1, "market_paints", List.of(), false, List.of(), List.of(document(raw)))));
        assertEquals(List.of(guide), repository.snapshot.paintUsageGuides());
    }

    private static MarketCatalogApplicationService marketService(FakeRepository repository) {
        return new MarketCatalogApplicationService(() -> {
            var snapshot = repository.load();
            return MarketCatalogFactory.create(
                    snapshot.paintProducts(), snapshot.paintableProducts(), snapshot.marketPaintingGuides(), snapshot.paintCatalogEditions(), snapshot.paintUsageGuides());
        }, (products, text) -> products.stream()
                .filter(p -> text.isBlank() || p.name().toLowerCase(java.util.Locale.ROOT).contains(text.toLowerCase(java.util.Locale.ROOT)))
                .sorted(java.util.Comparator.comparing(com.minipaintdex.domain.market.paint.PaintProduct::name))
                .map(com.minipaintdex.domain.market.paint.PaintProduct::id).toList(), searchPolicy());
    }

    private static com.minipaintdex.application.query.PaintSearchPolicy searchPolicy() {
        return new com.minipaintdex.application.query.PaintSearchPolicy(8, 20, 200, 16, 5, 1, 50, 2000, 1000, 8, 3, 1, 0.8f, 0.2f);
    }

    private static FakeRepository repositoryWithImportedItem() {
        var repository = repository();
        repository.snapshot = snapshot(
                document(Map.of()), repository.snapshot.paintProducts(), List.of(), List.of(product()), List.of(), List.of(),
                List.of(
                        envelope("workshop", 1, "workshop", new WorkshopCreated("my-workshop", "My workshop", AT)),
                        envelope("project", 1, "project", new PaintingProjectCreated(
                                "paint-game", "my-workshop", "game", "Paint Game", 1, AT)),
                        envelope("project-active", 2, "project-active", new PaintingProjectStatusChanged(
                                "paint-game", PaintingProjectStatus.ACTIVE, AT)),
                        envelope("workshop-register", 2, "register", new PaintingProjectRegistered(
                                "my-workshop", "paint-game", AT)),
                        envelope("item", 1, "item", new WorkshopPaintableAdded(
                                "ws-game-hero-001", "game-hero", "paint-game", "Hero", 1, AT))), List.of());
        return repository;
    }

    private static EventEnvelope envelope(String id, long version, String key, DomainEvent event) {
        return new EventEnvelope(id, 1, version, AT, new Actor("user", "owner"),
                "correlation", null, key, event);
    }

    private static WorkshopApplicationService service(FakeRepository repository) {
        var policy = new PaintMatchingPolicy(
                5, Set.of("one_coat_shading", "washing", "priming", "effect_application"),
                2.5, 20, 25, 50, 50, 80, 75,
                new PaintMatchingPolicy.Weights(.65, .15, 0, .08, .07, .05),
                new PaintMatchingPolicy.Weights(.15, .35, .30, .10, .10, 0));
        var paintMatchEngine = new PaintMatchEngine(policy);
        var queries = new WorkshopQueryService(repository, paintMatchEngine);
        var commands = new WorkshopCommandService(
                repository, repository, repository,
                new WorkshopMediaPolicy(10 * 1024 * 1024, Set.of("image/jpeg", "image/png", "image/webp")),
                queries);
        return new WorkshopApplicationService(commands, queries, marketService(repository), repository, searchPolicy());
    }

    private static PaintableProduct product() {
        return new PaintableProduct(1, "game", "Game", "Game line", "board_game", "full set", 1,
                new PaintableProduct.Edition("", ""), List.of(),
                List.of(new PaintableProduct.PaintableComponent(
                        "game-hero", "game", "Hero", "hero", 1, "", false, List.of(), List.of())));
    }

    private static Map<String, Object> paint(String id, String name) {
        return Map.ofEntries(
                Map.entry("schema_version", 1), Map.entry("id", id),
                Map.entry("brand", "Warhammer Colour"), Map.entry("brand_aliases", List.of("Citadel")),
                Map.entry("manufacturer", "Games Workshop"), Map.entry("range", "Contrast"),
                Map.entry("profile", Map.of(
                        "roles", List.of("color_paint"), "application_methods", List.of("brush"),
                        "application_system", "one_coat_shading", "coverage", "transparent",
                        "finish", "matte", "effects", List.of(),
                        "undercoat", Map.of("tone", "light", "pre_highlighted_surface_recommended", true),
                        "medium", "water_based_acrylic")),
                Map.entry("reference", "29-34"), Map.entry("name", name),
                Map.entry("color", Map.of("hex", "#D9DEDA", "family", "White")), Map.entry("volume_ml", 18),
                Map.entry("lifecycle_status", "active"), Map.entry("tags", List.of("cold")),
                Map.entry("manufacturer_image", Map.of(
                        "image_quality", "none",
                        "quality_limitation", Map.of(
                                "code", "historical-reason-not-recorded",
                                "detail", "The precise historical reason was not recorded.",
                                "observed_at", "2026-09-01"))));
    }

    private static Map<String, Object> auxiliaryPaint() {
        var paint = new LinkedHashMap<>(paint("paint-auxiliary", "Airbrush thinner"));
        paint.put("profile", Map.of(
                "roles", List.of("auxiliary"), "application_methods", List.of("brush"),
                "application_system", "effect_application", "coverage", "unknown",
                "finish", "unknown", "effects", List.of(),
                "undercoat", Map.of("tone", "any", "pre_highlighted_surface_recommended", false),
                "medium", "water_based_acrylic"));
        paint.put("color", Map.of("hex", "", "family", "auxiliary"));
        paint.put("usage_instructions", Map.of(
                "summary", "Auxiliary product.", "steps", List.of("Use as directed."),
                "tips", List.of(), "review_required", false));
        return paint;
    }

    private static StructuredDocument document(Map<String, Object> values) {
        return new StructuredDocument(values.entrySet().stream()
                .map(entry -> new StructuredDocument.Field(entry.getKey(), documentValue(entry.getValue())))
                .toList());
    }

    private static List<StructuredDocument> documents(List<Map<String, Object>> values) {
        return values.stream().map(ApplicationServicesTest::document).toList();
    }

    private static Map<String, Object> documentMap(StructuredDocument document) {
        var result = new LinkedHashMap<String, Object>();
        document.fields().forEach(field -> result.put(field.name(), plainValue(field.value())));
        return result;
    }

    private static Object plainValue(StructuredDocument.Value value) {
        return switch (value) {
            case StructuredDocument.Text text -> text.value();
            case StructuredDocument.NumberValue number -> number.value();
            case StructuredDocument.BooleanValue bool -> bool.value();
            case StructuredDocument.NullValue ignored -> null;
            case StructuredDocument.ArrayValue array -> array.values().stream()
                    .map(ApplicationServicesTest::plainValue).toList();
            case StructuredDocument.ObjectValue object -> documentMap(object.value());
        };
    }

    private static StructuredDocument.Value documentValue(Object value) {
        if (value == null) return new StructuredDocument.NullValue();
        if (value instanceof Map<?, ?> values) {
            var normalized = new LinkedHashMap<String, Object>();
            values.forEach((key, entry) -> normalized.put(String.valueOf(key), entry));
            return new StructuredDocument.ObjectValue(document(normalized));
        }
        if (value instanceof List<?> values) {
            return new StructuredDocument.ArrayValue(values.stream()
                    .map(ApplicationServicesTest::documentValue).toList());
        }
        if (value instanceof Number number) return new StructuredDocument.NumberValue(number);
        if (value instanceof Boolean bool) return new StructuredDocument.BooleanValue(bool);
        return new StructuredDocument.Text(String.valueOf(value));
    }

    @Test
    void importsPhysicalPotsIdempotentlyAndPreservesPrivateState() {
        var repository = repository();
        var service = service(repository);
        var productId = "warhammer-colour-contrast-apothecary-white";
        var registrations = List.of(new ImportPaintPotsCommand.Registration("pot-one", productId, null),
                new ImportPaintPotsCommand.Registration("pot-two", productId, null));
        var preview = service.importPaintPots(new ImportPaintPotsCommand(1, "workshop_paint_pots", registrations, true, "owner", "pots", "batch-a"));
        assertEquals(2, preview.added());
        assertTrue(repository.batches.isEmpty());
        service.importPaintPots(new ImportPaintPotsCommand(1, "workshop_paint_pots", registrations, false, "owner", "pots", "batch-a"));
        service.observePaintPot(new ObservePaintPotCommand("pot-one", "thickened", "low", "owner", AT, "pots", "observation-a"));
        service.addPaintPotNote(new AddPaintPotNoteCommand("pot-one", "Needs stirring", "owner", AT, "pots", "note-a"));
        var photo = new AddPaintPotPhotoCommand("pot-one", "my-pot.png", "image/png", new byte[]{1, 2, 3}, "My pot", "owner", AT, "pots", "photo-a");
        service.addPaintPotPhoto(photo);
        service.addPaintPotPhoto(photo);
        var before = service.getPaintPot("pot-one");
        var repeated = service.importPaintPots(new ImportPaintPotsCommand(1, "workshop_paint_pots", registrations, false, "owner", "pots", "batch-b"));
        assertEquals(0, repeated.added());
        assertEquals(2, repeated.existing());
        assertEquals(before, service.getPaintPot("pot-one"));
        assertEquals(1, repository.mediaStored);
        assertEquals(1, before.photos().size());
        assertEquals("unknown", service.getPaintPot("pot-two").remainingLevel());
        service.observePaintPot(new ObservePaintPotCommand("pot-one", "dried", "empty", "owner", AT, "pots", "observation-b"));
        assertFalse(service.getPaintPot("pot-one").available());
        assertTrue(service.getPaintPot("pot-two").available());
        assertTrue(repository.replaced.isEmpty());
        var beforeLoads = repository.loads;
        var page = service.searchPaintPots(new com.minipaintdex.application.query.SearchPaintPotsQuery(null, false,
                new com.minipaintdex.application.query.PageQuery(0, 200, List.of())));
        assertEquals(2, page.totalElements());
        assertEquals(2, repository.loads - beforeLoads, "One workshop and one Market generation per page");
    }

    @Test
    void rejectsInvalidPotBatchAndCrossContextStockMutationsBeforeWriting() {
        var repository = repository();
        var service = service(repository);
        var productId = "warhammer-colour-contrast-apothecary-white";
        assertThrows(DomainException.class, () -> service.importPaintPots(new ImportPaintPotsCommand(1, "workshop_paint_pots",
                List.of(new ImportPaintPotsCommand.Registration("pot-ok", productId, null),
                        new ImportPaintPotsCommand.Registration("pot-bad", "missing-product", null)),
                false, null, "test", "invalid")));
        assertTrue(repository.batches.isEmpty());
        var admin = new AdministrationApplicationService(repository, repository, repository);
        assertThrows(DomainException.class, () -> admin.applyPaintProductChangeSet(new ApplyPaintProductChangeSetCommand(1, "market_paints",
                List.of(new ApplyPaintProductChangeSetCommand.Operation("upsert", null, document(paint("new-paint", "New Paint")), 1, false)), false, List.of(), List.of())));
        service.registerPaintPot(new RegisterPaintPotCommand("pot-owned", productId, null, null, "test", "owned"));
        assertThrows(DomainException.class, () -> admin.applyPaintProductChangeSet(new ApplyPaintProductChangeSetCommand(1, "market_paints",
                List.of(new ApplyPaintProductChangeSetCommand.Operation("rekey", productId, document(paint("new-paint", "New Paint")), 0, false)), false, List.of(), List.of())));
        assertTrue(repository.replaced.isEmpty());
    }

    private static final class FakeRepository implements SnapshotRepository, EventBus, PaintProductCatalogWriter,
            PaintableProductCatalogWriter, WorkshopMediaStorage, PersistenceLifecycle {
        private DataSnapshot snapshot;
        private final List<EventBatch> batches = new ArrayList<>();
        private final Map<String, EventPublication> publications = new LinkedHashMap<>();
        private List<StructuredDocument> replaced = List.of();
        private List<StructuredDocument> inventory = List.of();
        private List<StructuredDocument> guides = List.of();
        private List<StructuredDocument> shopping = List.of();
        private int mediaStored;
        private int loads;

        private FakeRepository(DataSnapshot snapshot) { this.snapshot = snapshot; }
        @Override public DataSnapshot load() { loads++; return snapshot; }
        @Override public PublicationReceipt publish(EventBatch batch) {
            batches.add(batch);
            var events = new ArrayList<>(snapshot.events());
            events.addAll(batch.events());
            snapshot = new DataSnapshot(snapshot.site(), snapshot.paintProducts(),
                    snapshot.paintableProducts(), snapshot.marketPaintingGuides(), snapshot.shopping(), List.copyOf(events), snapshot.paintCatalogEditions(), snapshot.paintUsageGuides());
            var publication = new EventPublication(
                    batch.batchId(), EventPublicationStatus.COMPLETED, batch, batch.acceptedAt(), batch.acceptedAt(), 1, null);
            publications.put(batch.batchId(), publication);
            return new PublicationReceipt(batch.batchId(), publication.status(), batch.acceptedAt(), batch.correlationId());
        }
        @Override public Optional<EventPublication> publication(String publicationId) { return Optional.ofNullable(publications.get(publicationId)); }
        @Override public EventPublication await(String publicationId, Duration timeout) { return publications.get(publicationId); }
        @Override public EventBusState state() { return new EventBusState(true, true, 0, 0); }
        @Override public InitializationReport initialize() { return new InitializationReport(status(), snapshot.paintProducts().size(), snapshot.paintableProducts().size(), snapshot.events().size()); }
        @Override public RefreshResult refreshIfChanged() { return new RefreshResult(false, status()); }
        @Override public PersistenceStatus status() { return new PersistenceStatus("ready", "test", 1, "fixture", AT, AT, AT, null); }
        @Override public void replacePaintProducts(List<StructuredDocument> paints) { replaced = List.copyOf(paints); }
        @Override public void replacePaintProductCatalog(List<StructuredDocument> paints, List<StructuredDocument> editions, List<StructuredDocument> usageGuides) {
            replaced = List.copyOf(paints);
            snapshot = new DataSnapshot(snapshot.site(), paints, snapshot.paintableProducts(),
                    snapshot.marketPaintingGuides(), snapshot.shopping(), snapshot.events(), editions, usageGuides);
        }
        @Override public void replacePaintProductIdentities(
                List<StructuredDocument> paints,
                List<StructuredDocument> paintingGuides,
                List<StructuredDocument> shopping) {
            replaced = List.copyOf(paints);
            guides = List.copyOf(paintingGuides);
            this.shopping = List.copyOf(shopping);
        }
        @Override public void replaceProduct(
                String paintableProductId, StructuredDocument product, List<StructuredDocument> guides) { }
        @Override public StoredMedia store(String workshopPaintableId, String mediaId, String filename, String contentType, byte[] content) {
            mediaStored++;
            return new StoredMedia(mediaId, "/media/" + mediaId, "workshop/" + mediaId, filename, contentType,
                    content.length, "0".repeat(64));
        }
        @Override public void delete(StoredMedia media) { }
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
        return new DataSnapshot(site, paints, products, guides, shopping, events, editions, java.util.List.of());
    }

}
