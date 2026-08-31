package com.minipaintdex.application;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.AddWorkshopItemCommentCommand;
import com.minipaintdex.application.command.AddWorkshopItemPhotoCommand;
import com.minipaintdex.application.command.ApplyMarketPaintChangeSetCommand;
import com.minipaintdex.application.command.ApplyMarketPaintableProductChangeSetCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.command.TransitionStageCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.command.TransitionPaintingProjectCommand;
import com.minipaintdex.application.command.SetShoppingItemStatusCommand;
import com.minipaintdex.application.command.ReplaceWorkshopPaintInventoryCommand;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventLedger;
import com.minipaintdex.application.port.EventBus;
import com.minipaintdex.application.event.EventBatch;
import com.minipaintdex.application.event.EventPublicationStatus;
import com.minipaintdex.application.event.PublicationReceipt;
import com.minipaintdex.application.port.MarketPaintCatalogWriter;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.port.PaintableProductCatalogWriter;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.WorkshopPaintInventoryWriter;
import com.minipaintdex.application.port.WorkshopMediaStorage;
import com.minipaintdex.application.result.ApplyMarketPaintChangeSetResult;
import com.minipaintdex.application.result.ApplyMarketPaintableProductChangeSetResult;
import com.minipaintdex.application.result.CreatePaintingProjectResult;
import com.minipaintdex.application.result.ReplaceWorkshopPaintInventoryResult;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.DashboardView;
import com.minipaintdex.application.view.MissingPaintView;
import com.minipaintdex.application.view.PaintableProductSummaryView;
import com.minipaintdex.application.view.PaintableProductView;
import com.minipaintdex.application.view.PaintingProjectView;
import com.minipaintdex.application.view.ProductImportPreviewView;
import com.minipaintdex.application.view.RebuildProjectionResult;
import com.minipaintdex.application.view.ShoppingItemView;
import com.minipaintdex.application.view.WorkshopItemView;
import com.minipaintdex.application.view.WorkshopOverviewView;
import com.minipaintdex.application.view.WorkshopRecipeView;
import com.minipaintdex.application.view.GuideReconciliationView;
import com.minipaintdex.application.view.MarketPaintingGuideView;
import com.minipaintdex.application.view.SiteConfigurationView;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.AggregateRoot;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.market.paint.PaintMatchEngine;
import com.minipaintdex.domain.market.product.PaintableProduct;
import com.minipaintdex.domain.shared.DomainException;
import com.minipaintdex.domain.workshop.StageAction;
import com.minipaintdex.domain.workshop.WorkflowStage;
import com.minipaintdex.domain.workshop.WorkflowStageStatus;
import com.minipaintdex.domain.workshop.WorkshopItemProjector;
import com.minipaintdex.domain.workshop.WorkshopItemState;
import com.minipaintdex.domain.workshop.PaintingProjectProjector;
import com.minipaintdex.domain.workshop.PaintingProject;
import com.minipaintdex.domain.workshop.WorkshopRecipeProjector;
import com.minipaintdex.domain.workshop.WorkshopRecipeState;
import com.minipaintdex.domain.workshop.WorkshopRecipeStatus;
import com.minipaintdex.domain.workshop.Workshop;
import com.minipaintdex.domain.workshop.WorkshopProjector;
import com.minipaintdex.domain.workshop.PaintingProjectStatus;
import com.minipaintdex.domain.workshop.PaintingProjectEvent;
import com.minipaintdex.domain.workshop.RecipeSolution;
import com.minipaintdex.domain.workshop.ShoppingItem;
import com.minipaintdex.domain.workshop.ShoppingItemEvent;
import com.minipaintdex.domain.workshop.ShoppingItemStatusChanged;
import com.minipaintdex.domain.workshop.WorkshopEvent;
import com.minipaintdex.domain.workshop.WorkshopItem;
import com.minipaintdex.domain.workshop.WorkshopItemEvent;
import com.minipaintdex.domain.workshop.WorkshopRecipe;
import com.minipaintdex.domain.workshop.WorkshopRecipeEvent;
import com.minipaintdex.domain.workshop.WorkshopRecipeCreated;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application composition retained while use cases are exposed through segregated input ports.
 * Mutating methods serialize the local load-decide-durable-accept window so a later command sees
 * every earlier accepted event; the ledger additionally enforces aggregate versions under its file lock.
 */
public final class MiniPaintDexService {
    private static final Set<String> TECHNICAL_PAINT_TYPES = Set.of(
            "technical_effect", "primer", "wash_shade", "ink", "auxiliary");
    private static final Set<String> RECIPE_SOLUTION_TYPES = Set.of(
            "single_paint", "mixture", "layer_stack", "technique");
    private final PaintMatchEngine paintMatchEngine;
    private final SnapshotRepository snapshots;
    private final EventBus eventBus;
    private final MarketPaintCatalogWriter marketPaints;
    private final WorkshopPaintInventoryWriter workshopPaints;
    private final PaintableProductCatalogWriter paintableProducts;
    private final WorkshopMediaStorage mediaStorage;
    private final MarketPaintQueryService paintQueries;
    private final WorkshopMediaPolicy mediaPolicy;
    private final DomainEventEnvelopeFactory envelopeFactory = new DomainEventEnvelopeFactory();

    public MiniPaintDexService(
            SnapshotRepository snapshots,
            EventBus eventBus,
            MarketPaintCatalogWriter marketPaints,
            WorkshopPaintInventoryWriter workshopPaints,
            PaintableProductCatalogWriter paintableProducts,
            WorkshopMediaStorage mediaStorage,
            WorkshopMediaPolicy mediaPolicy,
            PaintMatchEngine paintMatchEngine) {
        this.snapshots = Objects.requireNonNull(snapshots);
        this.eventBus = Objects.requireNonNull(eventBus);
        this.marketPaints = Objects.requireNonNull(marketPaints);
        this.workshopPaints = Objects.requireNonNull(workshopPaints);
        this.paintableProducts = Objects.requireNonNull(paintableProducts);
        this.mediaStorage = Objects.requireNonNull(mediaStorage);
        this.mediaPolicy = Objects.requireNonNull(mediaPolicy);
        this.paintQueries = new MarketPaintQueryService(() -> marketCatalog(this.snapshots.load()));
        this.paintMatchEngine = Objects.requireNonNull(paintMatchEngine);
    }

    public SiteConfigurationView siteConfiguration() {
        return new SiteConfigurationView(configurationSection(documentMap(snapshots.load().site()), true));
    }

    public DashboardView dashboard() {
        var snapshot = snapshots.load();
        var items = WorkshopItemProjector.project(snapshot.events());
        var projects = PaintingProjectProjector.project(snapshot.events());
        var ownedPaintIds = documentMaps(snapshot.paintInventory()).stream()
                .filter(entry -> number(entry.get("quantity")) > 0)
                .map(entry -> text(entry.get("paint_id"))).collect(Collectors.toSet());
        var completedItems = items.stream().filter(WorkshopItemState::completed).count();
        return new DashboardView(
                new DashboardView.PaintStats(
                        snapshot.marketPaints().size(), ownedPaintIds.size(),
                        documentMaps(snapshot.marketPaints()).stream().map(paint -> text(paint.get("brand")))
                                .filter(MiniPaintDexService::present).distinct().count()),
                snapshot.paintableProducts().size(),
                new DashboardView.WorkshopStats(
                        projects.size(), items.size(), completedItems,
                        items.isEmpty() ? 0 : Math.round(completedItems * 100f / items.size())));
    }

    public synchronized ApplyMarketPaintChangeSetResult applyMarketPaintChangeSet(ApplyMarketPaintChangeSetCommand command) {
        if (command.schemaVersion() != 1) throw new DomainException("invalid_input", "schemaVersion must be 1.");
        if (!"market_paints".equals(command.kind())) throw new DomainException("invalid_input", "kind must be market_paints.");
        if (command.operations().isEmpty()) throw new DomainException("invalid_input", "At least one operation is required.");
        var snapshot = snapshots.load();
        var current = documentMaps(snapshot.marketPaints());
        var byId = new LinkedHashMap<String, Map<String, Object>>();
        current.forEach(paint -> byId.put(text(paint.get("id")), paint));
        var added = 0;
        var updated = 0;
        var retired = 0;
        var deleted = 0;
        var unchanged = 0;
        var inventoryChanged = 0;
        var quantities = new LinkedHashMap<String, Integer>();
        documentMaps(snapshot.paintInventory()).forEach(entry -> quantities.merge(
                text(entry.get("paint_id")), number(entry.get("quantity")), Integer::sum));
        var referencedPaintIds = referencedPaintIds(snapshot);
        for (var operation : command.operations()) {
            var record = documentMap(operation.record());
            var id = text(record.get("id"));
            require(id, "record.id");
            if ("upsert".equals(operation.action())) {
                validateMarketPaint(record);
                var previous = byId.put(id, new LinkedHashMap<>(record));
                if (previous == null) added++;
                else if (previous.equals(record)) unchanged++;
                else updated++;
            } else if ("retire".equals(operation.action())) {
                var previous = byId.get(id);
                if (previous == null) throw new DomainException("not_found", "Paint not found: " + id);
                var replacement = new LinkedHashMap<>(previous);
                replacement.put("lifecycle_status", defaultText(text(record.get("lifecycle_status")), "discontinued"));
                if (present(text(record.get("verified_at")))) replacement.put("verified_at", record.get("verified_at"));
                if (present(text(record.get("removal_reason")))) replacement.put("removal_reason", record.get("removal_reason"));
                if (previous.equals(replacement)) unchanged++;
                else {
                    byId.put(id, replacement);
                    retired++;
                }
            } else if ("delete".equals(operation.action())) {
                if (!operation.confirmedRemoval()) throw new DomainException("invalid_input", "Paint deletion requires confirmedRemoval.");
                if (quantities.getOrDefault(id, 0) > 0) throw new DomainException("conflict", "Owned paint cannot be deleted; retire it instead: " + id);
                if (referencedPaintIds.contains(id)) throw new DomainException("conflict", "Paint referenced by a market guide or workshop recipe cannot be deleted; retire it instead: " + id);
                if (byId.remove(id) == null) throw new DomainException("not_found", "Paint not found: " + id);
                deleted++;
            } else {
                throw new DomainException("invalid_input", "Unsupported paint operation: " + operation.action());
            }
            if (operation.workshopQuantityDelta() < 0) {
                throw new DomainException("invalid_input", "workshopQuantityDelta cannot be negative.");
            }
            if (operation.workshopQuantityDelta() > 0 && !"delete".equals(operation.action())) {
                quantities.merge(id, operation.workshopQuantityDelta(), Integer::sum);
                inventoryChanged++;
            }
        }
        var result = byId.values().stream()
                .sorted(Comparator.comparing(paint -> text(paint.get("id"))))
                .map(Map::copyOf)
                .toList();
        var inventory = quantities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.<String, Object>of("paint_id", entry.getKey(), "quantity", entry.getValue()))
                .toList();
        if (!command.dryRun()) {
            if (inventoryChanged > 0) {
                marketPaints.replaceMarketPaintsAndWorkshopInventory(
                        structuredDocuments(result), structuredDocuments(inventory), workshopPaints);
            } else {
                marketPaints.replaceMarketPaints(structuredDocuments(result));
            }
        }
        return new ApplyMarketPaintChangeSetResult(
                added, updated, retired, deleted, unchanged, inventoryChanged, result.size(), !command.dryRun());
    }

    public synchronized ReplaceWorkshopPaintInventoryResult replaceWorkshopPaintInventory(
            ReplaceWorkshopPaintInventoryCommand command) {
        if (command.schemaVersion() != 1) {
            throw new DomainException("invalid_input", "schemaVersion must be 1.");
        }
        if (!"workshop_paints".equals(command.kind())) {
            throw new DomainException("invalid_input", "kind must be workshop_paints.");
        }
        var marketIds = documentMaps(snapshots.load().marketPaints()).stream()
                .map(paint -> text(paint.get("id"))).collect(Collectors.toSet());
        var inventory = new LinkedHashMap<String, Integer>();
        for (var entry : command.paints()) {
            require(entry.paintId(), "paints.paintId");
            if (!marketIds.contains(entry.paintId())) {
                throw new DomainException("not_found", "Market paint not found: " + entry.paintId());
            }
            if (entry.quantity() < 0) {
                throw new DomainException("invalid_input", "Paint quantity cannot be negative: " + entry.paintId());
            }
            if (inventory.putIfAbsent(entry.paintId(), entry.quantity()) != null) {
                throw new DomainException("invalid_input", "Duplicate workshop paint: " + entry.paintId());
            }
        }
        var normalized = inventory.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.<String, Object>of("paint_id", entry.getKey(), "quantity", entry.getValue()))
                .toList();
        if (!command.dryRun()) workshopPaints.replaceWorkshopPaints(structuredDocuments(normalized));
        return new ReplaceWorkshopPaintInventoryResult(
                normalized.size(), inventory.values().stream().mapToInt(Integer::intValue).sum(), !command.dryRun());
    }

    public synchronized ApplyMarketPaintableProductChangeSetResult applyMarketPaintableProductChangeSet(ApplyMarketPaintableProductChangeSetCommand command) {
        if (command.schemaVersion() != 1) throw new DomainException("invalid_input", "schemaVersion must be 1.");
        if (!"market_product".equals(command.kind())) throw new DomainException("invalid_input", "kind must be market_product.");
        var productDocument = documentMap(command.product());
        var guideDocuments = command.paintingGuides().stream().map(MiniPaintDexService::documentMap).toList();
        var product = product(productDocument);
        var catalogItemIds = product.catalogItems().stream().map(PaintableProduct.CatalogItem::id).collect(Collectors.toSet());
        var marketPaintIds = documentMaps(snapshots.load().marketPaints()).stream()
                .map(paint -> text(paint.get("id"))).collect(Collectors.toSet());
        for (var guide : guideDocuments) {
            var guideId = text(guide.get("id"));
            require(guideId, "painting_guides.id");
            var catalogItemId = text(guide.get("catalog_item_id"));
            if (!catalogItemIds.contains(catalogItemId)) {
                throw new DomainException("invalid_input", "Painting guide references an unknown catalog item: " + catalogItemId);
            }
            require(text(guide.get("knowledge_status")), "painting_guides.knowledge_status");
            if (number(guide.get("version")) < 1) throw new DomainException("invalid_input", "Painting guide version must be positive.");
            for (var slot : listOfMaps(guide.get("slots"))) {
                require(text(slot.get("id")), "painting_guides.slots.id");
                var paintId = text(slot.get("market_paint_id"));
                if (present(paintId) && !marketPaintIds.contains(paintId)) {
                    throw new DomainException("invalid_input", "Painting guide references an unknown market paint: " + paintId);
                }
                if (!present(paintId) && !Boolean.TRUE.equals(slot.get("pending_import"))) {
                    throw new DomainException("invalid_input", "Painting guide slot must reference the market or be pending_import.");
                }
            }
        }
        if (!command.dryRun()) {
            paintableProducts.replaceProduct(
                    product.id(), structuredDocument(productDocument), structuredDocuments(guideDocuments));
        }
        return new ApplyMarketPaintableProductChangeSetResult(
                product.id(), product.catalogItems().size(), guideDocuments.size(), !command.dryRun());
    }

    public WorkshopOverviewView workshopOverview() {
        var snapshot = snapshots.load();
        var items = WorkshopItemProjector.project(snapshot.events());
        return workshopView(snapshot, paintableProductSummaries(snapshot), items);
    }

    public List<PaintingProjectView> listPaintingProjects() {
        return workshopOverview().paintingProjects();
    }

    public List<WorkshopItemView> listWorkshopItems(String paintingProjectId) {
        var items = WorkshopItemProjector.project(snapshots.load().events()).stream()
                .map(this::workshopItemView).toList();
        if (!present(paintingProjectId)) return items;
        return items.stream().filter(item -> paintingProjectId.equals(item.paintingProjectId())).toList();
    }

    public List<ShoppingItemView> listShoppingItems() {
        return shoppingViews(snapshots.load());
    }

    public WorkshopItemView.Detail getWorkshopItem(String itemId) {
        require(itemId, "itemId");
        var snapshot = snapshots.load();
        var item = WorkshopItemProjector.project(snapshot.events()).stream()
                .filter(candidate -> itemId.equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop item not found: " + itemId));
        return new WorkshopItemView.Detail(workshopItemView(item), snapshot.events().stream()
                .filter(event -> itemId.equals(event.aggregateId()))
                .sorted(Comparator.comparing(EventEnvelope::recordedAt).reversed()).toList());
    }

    public ProductImportPreviewView previewProductImport(String productId) {
        var snapshot = snapshots.load();
        var product = findProduct(snapshot, productId);
        return importPreview(snapshot, product);
    }

    private ProductImportPreviewView importPreview(DataSnapshot snapshot, PaintableProduct product) {
        var catalogIds = product.catalogItems().stream().map(PaintableProduct.CatalogItem::id).collect(Collectors.toSet());
        var guides = documentMaps(snapshot.marketPaintingGuides()).stream()
                .filter(guide -> catalogIds.contains(text(guide.get("catalog_item_id")))).toList();
        var requiredPaintIds = new java.util.LinkedHashSet<String>();
        var pendingSlots = 0;
        for (var guide : guides) {
            for (var slot : listOfMaps(guide.get("slots"))) {
                var paintId = text(slot.get("market_paint_id"));
                if (present(paintId)) requiredPaintIds.add(paintId);
                else if (Boolean.TRUE.equals(slot.get("pending_import"))) pendingSlots++;
            }
        }
        var ownedPaintIds = documentMaps(snapshot.paintInventory()).stream()
                .filter(entry -> number(entry.get("quantity")) > 0)
                .map(entry -> text(entry.get("paint_id"))).collect(Collectors.toSet());
        var paintsById = documentMaps(snapshot.marketPaints()).stream()
                .collect(Collectors.toMap(paint -> text(paint.get("id")), Function.identity()));
        var missingPaints = requiredPaintIds.stream().filter(id -> !ownedPaintIds.contains(id)).map(id -> {
            var paint = paintsById.getOrDefault(id, Map.of());
            return new MissingPaintView(
                    id, text(paint.get("name")), text(paint.get("brand")), text(paint.get("reference")));
        }).toList();
        var paintingProjects = PaintingProjectProjector.project(snapshot.events());
        return new ProductImportPreviewView(
                product.id(), product.name(), product.catalogItems().size(), product.expectedPaintableCount(),
                guides.size(), requiredPaintIds.size(), missingPaints.size(), missingPaints, pendingSlots,
                paintingProjects.stream().anyMatch(project -> product.id().equals(project.paintableProductId())));
    }

    public synchronized CreatePaintingProjectResult createPaintingProject(CreatePaintingProjectCommand command) {
        require(command.paintableProductId(), "paintableProductId");
        var snapshot = snapshots.load();
        var product = findProduct(snapshot, command.paintableProductId());
        var paintingProjects = PaintingProjectProjector.project(snapshot.events());
        var existingProject = paintingProjects.stream()
                .filter(project -> product.id().equals(project.paintableProductId()))
                .findFirst().orElse(null);
        var paintingProjectId = defaultText(command.paintingProjectId(), product.id());
        var paintingProjectName = defaultText(command.name(), product.name());
        var existingItems = WorkshopItemProjector.project(snapshot.events());
        var existingForProduct = (int) existingItems.stream()
                .filter(item -> (existingProject == null ? paintingProjectId : existingProject.id())
                        .equals(item.paintingProjectId())).count();
        if (existingProject != null) {
            return new CreatePaintingProjectResult(
                    Workshop.DEFAULT_ID, existingProject.id(), product.id(), 0, existingForProduct, true, false, null);
        }

        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var recordedAt = Instant.now();
        var correlationId = defaultText(command.correlationId(), Ulid.next(recordedAt));
        var actor = new Actor("user", defaultText(command.actorId(), "owner"));
        var baseKey = defaultText(command.idempotencyKey(), "create-painting-project:" + paintingProjectId);
        var events = new ArrayList<EventEnvelope>();
        var workshop = workshopAggregate(snapshot);
        if (workshop.id() == null) {
            workshop = Workshop.create(Workshop.DEFAULT_ID, "My workshop", occurredAt);
            events.addAll(envelopeFactory.envelop(
                    workshop, actor, correlationId, null, baseKey + ":workshop", recordedAt));
        }
        var paintingProject = PaintingProject.create(
                paintingProjectId, Workshop.DEFAULT_ID, product.id(), paintingProjectName,
                product.expectedPaintableCount(), occurredAt);
        paintingProject.changeStatus(PaintingProjectStatus.ACTIVE, occurredAt);
        events.addAll(envelopeFactory.envelop(
                paintingProject, actor, correlationId, null, baseKey, recordedAt));
        workshop.registerPaintingProject(paintingProjectId, occurredAt);
        events.addAll(envelopeFactory.envelop(
                workshop, actor, correlationId, null, baseKey + ":register", recordedAt));

        var existingIds = existingItems.stream().map(WorkshopItemState::id).collect(Collectors.toSet());
        var added = 0;
        for (var catalogItem : product.catalogItems()) {
            for (var ordinal = 1; ordinal <= catalogItem.quantity(); ordinal++) {
                var itemId = "ws-" + catalogItem.id() + "-" + String.format(Locale.ROOT, "%03d", ordinal);
                if (existingIds.contains(itemId)) continue;
                var displayName = catalogItem.quantity() == 1
                        ? catalogItem.name()
                        : catalogItem.name() + " #" + ordinal;
                var workshopItem = WorkshopItem.create(
                        itemId, catalogItem.id(), paintingProjectId, displayName, ordinal, occurredAt);
                events.addAll(envelopeFactory.envelop(
                        workshopItem, actor, correlationId, null, baseKey + ":" + itemId, recordedAt));
                added++;
            }
        }
        var publication = publish(events, correlationId, baseKey);
        return new CreatePaintingProjectResult(
                Workshop.DEFAULT_ID, paintingProjectId, product.id(), added, existingForProduct, false, true, publication);
    }

    public synchronized PublicationReceipt transitionPaintingProject(TransitionPaintingProjectCommand command) {
        require(command.paintingProjectId(), "paintingProjectId");
        require(command.targetStatus(), "targetStatus");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var project = paintingProjectAggregate(snapshot, command.paintingProjectId());
        project.changeStatus(PaintingProjectStatus.fromId(command.targetStatus()),
                command.occurredAt() == null ? Instant.now() : command.occurredAt());
        return publish(project,
                new Actor("user", defaultText(command.actorId(), "owner")),
                command.correlationId(), command.idempotencyKey());
    }

    public GuideReconciliationView reconcileMarketPaintingGuide(String guideId) {
        require(guideId, "guideId");
        var snapshot = snapshots.load();
        var guide = documentMaps(snapshot.marketPaintingGuides()).stream()
                .filter(candidate -> guideId.equals(text(candidate.get("id"))))
                .findFirst().orElseThrow(() -> new DomainException("not_found", "Market painting guide not found: " + guideId));
        var paintsById = documentMaps(snapshot.marketPaints()).stream()
                .collect(Collectors.toMap(paint -> text(paint.get("id")), Function.identity()));
        var ownedIds = documentMaps(snapshot.paintInventory()).stream()
                .filter(entry -> number(entry.get("quantity")) > 0)
                .map(entry -> text(entry.get("paint_id"))).collect(Collectors.toSet());
        var ownedProfiles = ownedIds.stream().map(paintsById::get).filter(Objects::nonNull)
                .map(this::paintProfile).toList();
        var paintViewsById = paintQueries.views(marketCatalog(snapshot)).stream()
                .collect(Collectors.toMap(MarketPaintView::id, Function.identity()));
        var slots = listOfMaps(guide.get("slots")).stream().map(slot -> {
            var sourcePaintId = text(slot.get("market_paint_id"));
            var sourcePaint = paintsById.get(sourcePaintId);
            var candidates = sourcePaint == null ? List.<GuideReconciliationView.PaintMatchView>of() : paintMatchEngine
                    .rank(paintProfile(sourcePaint), ownedProfiles).stream()
                    .map(match -> new GuideReconciliationView.PaintMatchView(
                            paintViewsById.get(match.candidatePaintId()), match.score(), match.deltaE2000(),
                            match.requiresManualReview(), match.strategy(),
                            new GuideReconciliationView.DimensionsView(
                                    match.colorScore(), match.functionalTypeScore(), match.behaviorScore(),
                                    match.finishScore(), match.opacityScore(), match.mediumScore()),
                            match.reasons()))
                    .toList();
            return new GuideReconciliationView.SlotReconciliationView(
                    marketPaintingGuideSlotView(slot), paintViewsById.get(sourcePaintId), candidates,
                    sourcePaint != null && paintMatchEngine.requiresManualReview(paintProfile(sourcePaint)));
        }).toList();
        return new GuideReconciliationView(marketPaintingGuideView(guide), slots, ownedProfiles.size());
    }

    public List<WorkshopRecipeView> listWorkshopRecipes(String catalogItemId) {
        return WorkshopRecipeProjector.project(snapshots.load().events()).stream()
                .filter(recipe -> !present(catalogItemId) || catalogItemId.equals(recipe.catalogItemId()))
                .map(this::workshopRecipeView)
                .sorted(Comparator.comparing(WorkshopRecipeView::id))
                .toList();
    }

    public synchronized PublicationReceipt createWorkshopRecipe(CreateWorkshopRecipeCommand command) {
        require(command.catalogItemId(), "catalogItemId");
        require(command.displayName(), "displayName");
        if (command.version() < 1) throw new DomainException("invalid_input", "version must be positive.");
        if (command.solutions().isEmpty()) throw new DomainException("invalid_input", "At least one recipe solution is required.");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var productId = paintableProductIdForCatalogItem(snapshot, command.catalogItemId());
        var paintingProject = PaintingProjectProjector.project(snapshot.events()).stream()
                .filter(project -> productId.equals(project.paintableProductId())).findFirst().orElse(null);
        if (paintingProject == null) {
            throw new DomainException("conflict", "Paintable product is not imported in the workshop: " + productId);
        }
        var recipes = WorkshopRecipeProjector.project(snapshot.events());
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var recipeId = present(command.recipeId()) ? command.recipeId()
                : "recipe-" + command.catalogItemId() + "-v" + command.version() + "-" + Ulid.next(occurredAt).toLowerCase(Locale.ROOT);
        if (recipes.stream().anyMatch(recipe -> recipeId.equals(recipe.id()))) {
            throw new DomainException("conflict", "Workshop recipe already exists: " + recipeId);
        }
        Map<String, Object> guide = Map.of();
        if (present(command.basedOnGuideId())) {
            guide = documentMaps(snapshot.marketPaintingGuides()).stream()
                    .filter(candidate -> command.basedOnGuideId().equals(text(candidate.get("id"))))
                    .findFirst().orElseThrow(() -> new DomainException("not_found", "Market painting guide not found: " + command.basedOnGuideId()));
            if (!command.catalogItemId().equals(text(guide.get("catalog_item_id")))) {
                throw new DomainException("conflict", "Market guide and workshop recipe must target the same catalog item.");
            }
        }
        if (present(command.supersedesRecipeId())) {
            var previous = recipes.stream().filter(recipe -> command.supersedesRecipeId().equals(recipe.id())).findFirst()
                    .orElseThrow(() -> new DomainException("not_found", "Superseded recipe not found: " + command.supersedesRecipeId()));
            if (!command.catalogItemId().equals(previous.catalogItemId()) || command.version() != previous.version() + 1) {
                throw new DomainException("conflict", "A recipe revision must target the same catalog item and increment the version by one.");
            }
        }
        validateRecipeSolutions(command.solutions(), guide, snapshot);
        var recipe = WorkshopRecipe.create(
                recipeId, paintingProject.id(), command.catalogItemId(), command.basedOnGuideId(),
                command.supersedesRecipeId(), command.displayName(), command.version(), command.solutions(), occurredAt);
        return publish(recipe,
                new Actor("user", defaultText(command.actorId(), "owner")),
                command.correlationId(), command.idempotencyKey());
    }

    public synchronized PublicationReceipt transitionWorkshopRecipe(TransitionWorkshopRecipeCommand command) {
        require(command.recipeId(), "recipeId");
        require(command.action(), "action");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var recipeState = WorkshopRecipeProjector.project(snapshot.events()).stream()
                .filter(candidate -> command.recipeId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop recipe not found: " + command.recipeId()));
        var recipe = workshopRecipeAggregate(snapshot, recipeState.id());
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        switch (command.action()) {
            case "validate" -> recipe.validate(occurredAt);
            case "activate" -> recipe.activate(occurredAt);
            case "supersede" -> {
                var successorId = requiredText(command.successorRecipeId(), "successorRecipeId");
                var successor = WorkshopRecipeProjector.project(snapshot.events()).stream()
                        .filter(candidate -> successorId.equals(candidate.id())).findFirst()
                        .orElseThrow(() -> new DomainException("not_found", "Successor recipe not found: " + successorId));
                if (!recipe.id().equals(successor.supersedesRecipeId())) {
                    throw new DomainException("conflict", "The successor recipe must reference the recipe it supersedes.");
                }
                recipe.supersede(successorId, occurredAt);
            }
            case "archive" -> recipe.archive(command.reason(), occurredAt);
            default -> throw new DomainException("invalid_input", "Unknown workshop recipe action: " + command.action());
        }
        return publish(recipe,
                new Actor("user", defaultText(command.actorId(), "owner")),
                command.correlationId(), command.idempotencyKey());
    }

    public synchronized PublicationReceipt assignWorkshopRecipe(AssignWorkshopRecipeCommand command) {
        require(command.itemId(), "itemId");
        require(command.recipeId(), "recipeId");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var item = WorkshopItemProjector.project(snapshot.events()).stream()
                .filter(candidate -> command.itemId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop item not found: " + command.itemId()));
        var recipe = WorkshopRecipeProjector.project(snapshot.events()).stream()
                .filter(candidate -> command.recipeId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop recipe not found: " + command.recipeId()));
        if (recipe.status() != WorkshopRecipeStatus.ACTIVE) {
            throw new DomainException("conflict", "Only an active workshop recipe can be assigned.");
        }
        if (!item.catalogItemId().equals(recipe.catalogItemId())) {
            throw new DomainException("conflict", "Workshop item and recipe must target the same catalog item.");
        }
        var workshopItem = workshopItemAggregate(snapshot, item.id());
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        workshopItem.assignRecipe(recipe.id(), recipe.version(), occurredAt);
        return publish(workshopItem,
                new Actor("user", defaultText(command.actorId(), "owner")),
                command.correlationId(), command.idempotencyKey());
    }

    public List<EventEnvelope> listActivity(String paintingProjectId) {
        var snapshot = snapshots.load();
        var itemIds = WorkshopItemProjector.project(snapshot.events()).stream()
                .filter(item -> !present(paintingProjectId) || paintingProjectId.equals(item.paintingProjectId()))
                .map(WorkshopItemState::id).collect(Collectors.toSet());
        return snapshot.events().stream()
                .filter(event -> !present(paintingProjectId)
                        || paintingProjectId.equals(event.projectId())
                        || itemIds.contains(event.aggregateId()))
                .sorted(Comparator.comparing(EventEnvelope::recordedAt).reversed())
                .toList();
    }

    public synchronized PublicationReceipt addWorkshopItem(AddWorkshopItemCommand command) {
        require(command.catalogItemId(), "catalogItemId");
        require(command.paintingProjectId(), "paintingProjectId");
        require(command.displayName(), "displayName");
        var snapshot = snapshots.load();
        var paintingProject = paintingProject(snapshot, command.paintingProjectId());
        var product = findProduct(snapshot, paintingProject.paintableProductId());
        if (!WorkshopProjector.project(snapshot.events()).containsPaintingProject(paintingProject.id())) {
            throw new DomainException("conflict", "Painting project is not registered in the workshop: " + paintingProject.id());
        }
        product.catalogItem(command.catalogItemId());
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var itemId = present(command.itemId()) ? command.itemId() : "ws-" + command.catalogItemId() + "-" + Ulid.next(occurredAt).toLowerCase(Locale.ROOT);
        if (snapshot.events().stream().anyMatch(event -> itemId.equals(event.aggregateId()) && "workshop_item.added".equals(event.eventType()))) {
            throw new DomainException("conflict", "Workshop item already exists: " + itemId);
        }
        var item = WorkshopItem.create(
                itemId, command.catalogItemId(), paintingProject.id(), command.displayName(), 0, occurredAt);
        return publish(item,
                new Actor("user", defaultText(command.actorId(), "owner")),
                command.correlationId(), command.idempotencyKey());
    }

    public synchronized PublicationReceipt transitionStage(TransitionStageCommand command) {
        require(command.itemId(), "itemId");
        var stage = WorkflowStage.fromId(command.stage());
        var action = StageAction.fromId(command.action());
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var item = WorkshopItemProjector.project(snapshot.events()).stream().filter(candidate -> command.itemId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop item not found: " + command.itemId()));
        var workshopItem = workshopItemAggregate(snapshot, item.id());
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var note = action == StageAction.SKIP ? command.reason() : command.comment();
        workshopItem.transition(stage, action, note, occurredAt);
        return publish(workshopItem,
                new Actor("user", defaultText(command.actorId(), "owner")),
                command.correlationId(), command.idempotencyKey());
    }

    public synchronized PublicationReceipt addWorkshopItemComment(AddWorkshopItemCommentCommand command) {
        require(command.itemId(), "itemId");
        require(command.comment(), "comment");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var item = WorkshopItemProjector.project(snapshot.events()).stream()
                .filter(candidate -> command.itemId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop item not found: " + command.itemId()));
        var workshopItem = workshopItemAggregate(snapshot, item.id());
        workshopItem.addComment(command.comment().trim(),
                command.occurredAt() == null ? Instant.now() : command.occurredAt());
        return publish(workshopItem,
                new Actor("user", defaultText(command.actorId(), "owner")),
                command.correlationId(), command.idempotencyKey());
    }

    public synchronized PublicationReceipt addWorkshopItemPhoto(AddWorkshopItemPhotoCommand command) {
        require(command.itemId(), "itemId");
        require(command.originalFilename(), "originalFilename");
        var contentType = defaultText(command.contentType(), "").toLowerCase(Locale.ROOT);
        if (!mediaPolicy.allowedContentTypes().contains(contentType)) {
            throw new DomainException("invalid_input", "Unsupported workshop photo content type: " + contentType);
        }
        var content = command.content();
        if (content.length == 0 || content.length > mediaPolicy.maxUploadBytes()) {
            throw new DomainException("invalid_input", "Workshop photo exceeds the configured upload limit.");
        }
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var item = WorkshopItemProjector.project(snapshot.events()).stream()
                .filter(candidate -> command.itemId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop item not found: " + command.itemId()));
        if (present(command.stage())) WorkflowStage.fromId(command.stage());
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var mediaId = "media-" + Ulid.next(occurredAt).toLowerCase(Locale.ROOT);
        var stored = mediaStorage.store(item.id(), mediaId, command.originalFilename(), contentType, content);
        var workshopItem = workshopItemAggregate(snapshot, item.id());
        workshopItem.addPhoto(
                stored.id(), stored.publicPath(), command.stage(), command.caption(), stored.originalFilename(),
                stored.contentType(), stored.size(), stored.sha256(), occurredAt);
        try {
            return publish(workshopItem,
                    new Actor("user", defaultText(command.actorId(), "owner")),
                    command.correlationId(), command.idempotencyKey());
        } catch (RuntimeException failure) {
            mediaStorage.delete(stored);
            throw failure;
        }
    }

    public synchronized PublicationReceipt setShoppingItemStatus(SetShoppingItemStatusCommand command) {
        require(command.itemId(), "itemId");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var known = shoppingViews(snapshot).stream().anyMatch(item -> command.itemId().equals(item.id()));
        if (!known) throw new DomainException("not_found", "Shopping item not found: " + command.itemId());
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var itemHistory = snapshot.events().stream()
                .filter(event -> command.itemId().equals(event.aggregateId()))
                .map(EventEnvelope::event)
                .filter(ShoppingItemEvent.class::isInstance)
                .map(ShoppingItemEvent.class::cast)
                .toList();
        var currentChecked = itemHistory.stream()
                .filter(ShoppingItemStatusChanged.class::isInstance)
                .map(ShoppingItemStatusChanged.class::cast)
                .reduce((left, right) -> right)
                .map(ShoppingItemStatusChanged::checked)
                .orElse(false);
        var shoppingItem = ShoppingItem.current(command.itemId(), currentChecked, itemHistory);
        shoppingItem.setChecked(command.checked(), occurredAt);
        if (shoppingItem.pendingEvents().isEmpty()) {
            return snapshot.events().stream()
                    .filter(event -> command.itemId().equals(event.aggregateId()))
                    .reduce((left, right) -> right)
                    .map(MiniPaintDexService::existingReceipt)
                    .orElseThrow(() -> new DomainException("no_change", "Shopping item status is already current."));
        }
        return publish(shoppingItem,
                new Actor("user", defaultText(command.actorId(), "owner")),
                command.correlationId(), command.idempotencyKey());
    }

    public synchronized RebuildProjectionResult rebuildProjections() {
        var snapshot = snapshots.load();
        return new RebuildProjectionResult(
                "rebuilt", "in_memory", snapshot.events().size(), Instant.now(),
                paintQueries.views(marketCatalog(snapshot)).size(), snapshot.paintableProducts().size(),
                PaintingProjectProjector.project(snapshot.events()).size(),
                WorkshopItemProjector.project(snapshot.events()).size(),
                snapshot.marketPaintingGuides().size(), WorkshopRecipeProjector.project(snapshot.events()).size());
    }

    private List<MarketPaintView> paintViews(DataSnapshot snapshot) {
        return paintQueries.views(marketCatalog(snapshot));
    }

    private List<PaintableProductView> paintableProductViews(DataSnapshot snapshot, List<MarketPaintView> paints) {
        var guides = documentMaps(snapshot.marketPaintingGuides()).stream().collect(Collectors.toMap(
                entry -> text(entry.get("catalog_item_id")), Function.identity(),
                (left, right) -> number(left.get("version")) >= number(right.get("version")) ? left : right));
        var paintsById = paints.stream().collect(Collectors.toMap(MarketPaintView::id, Function.identity()));
        return snapshot.paintableProducts().stream().map(product -> {
            var productItems = product.catalogItems().stream().map(item -> {
                var catalogItemId = item.id();
                var guide = guides.getOrDefault(catalogItemId, Map.of());
                var marketGuide = guide.isEmpty()
                        ? null
                        : new PaintableProductView.MarketGuideView(
                                text(guide.get("id")), number(guide.get("version")),
                                text(guide.get("knowledge_status")),
                                listOfMaps(guide.get("sources")).stream().map(this::sourceView).toList());
                return new PaintableProductView.CatalogItemView(
                        catalogItemId, product.id(), item.name(), item.kind(), item.quantity(), item.description(),
                        item.assemblyRequired(), item.referenceImages().stream().map(this::imageView).toList(),
                        listOfMaps(guide.get("slots")).stream().map(slot -> guideSlotView(slot, paintsById)).toList(),
                        listOfMaps(guide.get("preparation")).stream().map(this::stepView).toList(),
                        listOfMaps(guide.get("painting")).stream().map(this::stepView).toList(),
                        marketGuide, item.sources().stream().map(this::sourceView).toList());
            }).toList();
            return new PaintableProductView(
                    product.schemaVersion(), product.id(), product.name(), product.line(), product.productType(),
                    product.scope(), product.expectedPaintableCount(),
                    new PaintableProductView.EditionView(product.edition().note(), product.edition().url()),
                    product.sources().stream().map(this::sourceView).toList(), productItems);
        }).sorted(Comparator.comparing(PaintableProductView::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private List<PaintableProductSummaryView> paintableProductSummaries(DataSnapshot snapshot) {
        return snapshot.paintableProducts().stream().map(product -> new PaintableProductSummaryView(
                        product.id(), product.name(), product.line(), product.productType(), product.scope(),
                        product.catalogItems().size(), product.expectedPaintableCount()))
                .sorted(Comparator.comparing(PaintableProductSummaryView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private WorkshopOverviewView workshopView(
            DataSnapshot snapshot,
            List<PaintableProductSummaryView> marketProducts,
            List<WorkshopItemState> workshopItems) {
        var workshop = WorkshopProjector.project(snapshot.events());
        var paintingProjects = PaintingProjectProjector.project(snapshot.events());
        var marketById = marketProducts.stream().collect(Collectors.toMap(
                PaintableProductSummaryView::id, Function.identity()));
        var projects = paintingProjects.stream().map(project -> {
            var market = marketById.get(project.paintableProductId());
            if (market == null) return new PaintingProjectView(
                    project.id(), project.paintableProductId(), project.name(), project.status().id(),
                    project.createdAt(), project.updatedAt(), project.createdAt(),
                    0, 0, 0, 0, 0, 0, 0, List.of(), 0, true);
            var items = workshopItems.stream()
                    .filter(item -> project.id().equals(item.paintingProjectId())).toList();
            var completed = (int) items.stream().filter(WorkshopItemState::completed).count();
            var inProgress = (int) items.stream().filter(item -> !item.completed()
                    && item.workflow().values().stream().anyMatch(status -> status != WorkflowStageStatus.PENDING)).count();
            var stageUnits = items.size() * WorkflowStage.values().length;
            var finishedStages = items.stream().mapToInt(item -> (int) item.workflow().values().stream()
                    .filter(status -> status == WorkflowStageStatus.COMPLETED || status == WorkflowStageStatus.SKIPPED).count()).sum();
            var progress = stageUnits == 0 ? 0 : Math.round((finishedStages * 100f) / stageUnits);
            var preview = importPreview(snapshot, findProduct(snapshot, project.paintableProductId()));
            return new PaintingProjectView(
                    project.id(), project.paintableProductId(), market.name(), project.status().id(),
                    project.createdAt(), project.updatedAt(), project.createdAt(), items.size(), completed,
                    inProgress, items.size() - completed - inProgress, progress, preview.requiredPaintCount(),
                    preview.missingPaintCount(), preview.missingPaints(), preview.pendingPaintSlotCount(), false);
        }).toList();
        var completedItems = (int) workshopItems.stream().filter(WorkshopItemState::completed).count();
        return new WorkshopOverviewView(
                defaultText(workshop.id(), Workshop.DEFAULT_ID), projects, projects.size(), workshopItems.size(),
                completedItems,
                workshopItems.isEmpty() ? 0 : Math.round(completedItems * 100f / workshopItems.size()),
                snapshot.events().stream().sorted(Comparator.comparing(EventEnvelope::recordedAt).reversed())
                        .limit(12).toList());
    }

    private PaintableProductView.GuidePaintView guideSlotView(
            Map<String, Object> slot, Map<String, MarketPaintView> paintsById) {
        var paint = paintsById.get(text(slot.get("market_paint_id")));
        var requested = map(slot.get("requested_paint"));
        return new PaintableProductView.GuidePaintView(
                text(slot.get("id")), paint == null ? "" : paint.id(),
                paint == null ? text(requested.get("brand")) : paint.brand(),
                paint == null ? text(requested.get("name")) : paint.name(),
                text(slot.get("role")),
                paint == null ? text(requested.get("color_hex")) : paint.colorHex(),
                Boolean.TRUE.equals(slot.get("pending_import")));
    }

    private WorkshopItemView workshopItemView(WorkshopItemState item) {
        var workflow = new WorkshopItemView.WorkflowView(
                item.workflow().get(WorkflowStage.PREPARATION).id(),
                item.workflow().get(WorkflowStage.PRIMING).id(),
                item.workflow().get(WorkflowStage.PRE_HIGHLIGHT).id(),
                item.workflow().get(WorkflowStage.PAINTING).id(),
                item.workflow().get(WorkflowStage.FINISHING).id(),
                item.workflow().get(WorkflowStage.BASING).id());
        return new WorkshopItemView(
                item.id(), item.catalogItemId(), item.paintingProjectId(), item.displayName(), workflow,
                item.currentStage() == null ? null : item.currentStage().id(), item.completed(),
                defaultText(item.recipeId(), ""), item.recipeVersion(), item.updatedAt());
    }

    private List<ShoppingItemView> shoppingViews(DataSnapshot snapshot) {
        var paintsById = documentMaps(snapshot.marketPaints()).stream().collect(Collectors.toMap(
                paint -> text(paint.get("id")), Function.identity(), (left, right) -> left));
        var productsById = snapshot.paintableProducts().stream().collect(Collectors.toMap(
                PaintableProduct::id, PaintableProduct::name));
        var requiredByPaint = new LinkedHashMap<String, java.util.LinkedHashSet<String>>();
        var paintingProjects = PaintingProjectProjector.project(snapshot.events());
        for (var paintingProject : paintingProjects) {
            var product = snapshot.paintableProducts().stream()
                    .filter(candidate -> paintingProject.paintableProductId().equals(candidate.id())).findFirst().orElse(null);
            if (product == null) continue;
            for (var missing : importPreview(snapshot, product).missingPaints()) {
                requiredByPaint.computeIfAbsent(missing.id(), ignored -> new java.util.LinkedHashSet<>())
                        .add(product.id());
            }
        }
        var result = new ArrayList<ShoppingItemView>();
        var checkedById = new LinkedHashMap<String, Boolean>();
        snapshot.events().stream().filter(event -> "shopping_item.status_changed".equals(event.eventType()))
                .sorted(Comparator.comparing(EventEnvelope::recordedAt).thenComparing(EventEnvelope::eventId))
                .map(EventEnvelope::event)
                .filter(ShoppingItemStatusChanged.class::isInstance)
                .map(ShoppingItemStatusChanged.class::cast)
                .forEach(event -> checkedById.put(event.aggregateId(), event.checked()));
        var plannedPaintIds = new java.util.HashSet<String>();
        for (var entry : documentMaps(snapshot.shopping())) {
            var paintId = text(entry.get("market_paint_id"));
            var paint = paintsById.get(paintId);
            var sourceProducts = requiredByPaint.getOrDefault(paintId, new java.util.LinkedHashSet<>());
            result.add(new ShoppingItemView(
                    text(entry.get("id")),
                    paint == null ? text(entry.get("brand")) : text(paint.get("brand")),
                    paint == null ? text(entry.get("name")) : text(paint.get("name")),
                    paint == null ? text(entry.get("reference")) : text(paint.get("reference")),
                    paint == null ? text(entry.get("color_hex")) : text(map(paint.get("color")).get("hex")),
                    text(entry.get("reason")), defaultText(text(entry.get("priority")), "low"),
                    sourceProducts.isEmpty() ? "planned" : "required", true, paintId,
                    List.copyOf(sourceProducts),
                    sourceProducts.stream().map(id -> productsById.getOrDefault(id, id)).toList(),
                    checkedById.getOrDefault(text(entry.get("id")), false)));
            if (present(paintId)) plannedPaintIds.add(paintId);
        }
        requiredByPaint.forEach((paintId, sourceProducts) -> {
            if (plannedPaintIds.contains(paintId)) return;
            var paint = paintsById.getOrDefault(paintId, Map.of());
            result.add(new ShoppingItemView(
                    "required-" + paintId, text(paint.get("brand")), text(paint.get("name")),
                    text(paint.get("reference")), text(map(paint.get("color")).get("hex")), "", "high",
                    "required", false, paintId, List.copyOf(sourceProducts),
                    sourceProducts.stream().map(id -> productsById.getOrDefault(id, id)).toList(),
                    checkedById.getOrDefault("required-" + paintId, false)));
        });
        return List.copyOf(result);
    }

    private PaintableProductView.SourceView sourceView(Map<String, Object> source) {
        return new PaintableProductView.SourceView(
                text(source.get("kind")), text(source.get("label")), text(source.get("url")));
    }

    private MarketPaintingGuideView marketPaintingGuideView(Map<String, Object> guide) {
        return new MarketPaintingGuideView(
                text(guide.get("id")), text(guide.get("catalog_item_id")), number(guide.get("version")),
                text(guide.get("knowledge_status")),
                listOfMaps(guide.get("sources")).stream().map(this::sourceView).toList(),
                listOfMaps(guide.get("slots")).stream().map(this::marketPaintingGuideSlotView).toList(),
                listOfMaps(guide.get("preparation")).stream().map(this::stepView).toList(),
                listOfMaps(guide.get("painting")).stream().map(this::stepView).toList());
    }

    private MarketPaintingGuideView.SlotView marketPaintingGuideSlotView(Map<String, Object> slot) {
        var requested = map(slot.get("requested_paint"));
        return new MarketPaintingGuideView.SlotView(
                text(slot.get("id")), text(slot.get("role")), text(slot.get("market_paint_id")),
                Boolean.TRUE.equals(slot.get("pending_import")),
                new MarketPaintingGuideView.RequestedPaintView(
                        text(requested.get("brand")), text(requested.get("name")),
                        text(requested.get("color_hex"))));
    }

    private PaintableProductView.SourceView sourceView(PaintableProduct.Source source) {
        return new PaintableProductView.SourceView(source.kind(), source.label(), source.url());
    }

    private PaintableProductView.ReferenceImageView imageView(PaintableProduct.ReferenceImage image) {
        return new PaintableProductView.ReferenceImageView(
                image.url(), image.pageUrl(), image.credit(), image.license());
    }

    private PaintableProductView.GuideStepView stepView(Map<String, Object> step) {
        return new PaintableProductView.GuideStepView(text(step.get("title")), text(step.get("detail")));
    }

    private WorkshopRecipeView workshopRecipeView(WorkshopRecipeState recipe) {
        return new WorkshopRecipeView(
                recipe.id(), recipe.catalogItemId(), defaultText(recipe.basedOnGuideId(), ""),
                defaultText(recipe.supersedesRecipeId(), ""), recipe.displayName(), recipe.version(),
                recipe.status().id(), recipe.solutions(), recipe.updatedAt());
    }

    private static PaintableProduct findProduct(DataSnapshot snapshot, String productId) {
        require(productId, "productId");
        return snapshot.paintableProducts().stream().filter(product -> productId.equals(product.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Paintable product not found: " + productId));
    }

    private static MarketCatalogSnapshot marketCatalog(DataSnapshot snapshot) {
        return new MarketCatalogSnapshot(
                snapshot.marketPaints(), snapshot.paintableProducts(), snapshot.marketPaintingGuides());
    }

    private static PaintingProject paintingProject(DataSnapshot snapshot, String paintingProjectId) {
        require(paintingProjectId, "paintingProjectId");
        return PaintingProjectProjector.project(snapshot.events()).stream()
                .filter(project -> paintingProjectId.equals(project.id())).findFirst()
                .orElseThrow(() -> new DomainException(
                        "not_found", "Painting project not found: " + paintingProjectId));
    }

    private static PaintingProject paintingProjectForProduct(DataSnapshot snapshot, String productId) {
        return PaintingProjectProjector.project(snapshot.events()).stream()
                .filter(project -> productId.equals(project.paintableProductId())).findFirst()
                .orElseThrow(() -> new DomainException(
                        "conflict", "Paintable product is not part of a painting project: " + productId));
    }

    private static PaintableProduct product(Map<String, Object> document) {
        var edition = map(document.get("edition"));
        var sources = listOfMaps(document.get("sources")).stream().map(MiniPaintDexService::productSource).toList();
        var items = listOfMaps(document.get("catalog_items")).stream().map(item -> new PaintableProduct.CatalogItem(
                text(item.get("id")), text(item.get("product_id")), text(item.get("name")), text(item.get("kind")),
                number(item.get("quantity")), text(item.get("description")), Boolean.TRUE.equals(item.get("assembly_required")),
                listOfMaps(item.get("reference_images")).stream().map(image -> new PaintableProduct.ReferenceImage(
                        text(image.get("url")), text(image.get("page_url")), text(image.get("credit")), text(image.get("license")))).toList(),
                listOfMaps(item.get("sources")).stream().map(MiniPaintDexService::productSource).toList())).toList();
        return new PaintableProduct(
                number(document.getOrDefault("schema_version", 1)), text(document.get("id")), text(document.get("name")),
                text(document.get("line")), text(document.get("product_type")), text(document.get("scope")),
                number(document.get("expected_paintable_count")),
                new PaintableProduct.Edition(text(edition.get("note")), text(edition.get("url"))), sources, items);
    }

    private static PaintableProduct.Source productSource(Map<String, Object> source) {
        return new PaintableProduct.Source(text(source.get("kind")), text(source.get("label")), text(source.get("url")));
    }

    private PaintMatchEngine.Paint paintProfile(Map<String, Object> paint) {
        var behaviorTags = new java.util.LinkedHashSet<String>();
        behaviorTags.addAll(strings(paint.get("behavior_tags")));
        behaviorTags.addAll(strings(paint.get("tags")));
        map(paint.get("application_profile")).values().forEach(value -> {
            if (value instanceof List<?> values) values.forEach(entry -> behaviorTags.add(text(entry)));
            else if (present(text(value))) behaviorTags.add(text(value));
        });
        return new PaintMatchEngine.Paint(
                text(paint.get("id")), text(map(paint.get("color")).get("hex")),
                text(paint.get("functional_type")), text(paint.get("finish")), text(paint.get("opacity")),
                text(paint.get("medium")), Set.copyOf(behaviorTags));
    }

    private static Set<String> referencedPaintIds(DataSnapshot snapshot) {
        var result = new java.util.LinkedHashSet<String>();
        documentMaps(snapshot.marketPaintingGuides()).forEach(guide -> listOfMaps(guide.get("slots")).forEach(slot -> {
            var paintId = text(slot.get("market_paint_id"));
            if (present(paintId)) result.add(paintId);
        }));
        snapshot.events().stream().map(EventEnvelope::event)
                .filter(WorkshopRecipeCreated.class::isInstance)
                .map(WorkshopRecipeCreated.class::cast)
                .flatMap(event -> event.solutions().stream())
                .flatMap(solution -> solution.referencedPaintIds().stream())
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private static void collectPaintIds(Object value, Set<String> result) {
        if (value instanceof Map<?, ?> values) {
            values.forEach((key, entry) -> {
                if ("paint_id".equals(String.valueOf(key)) && present(text(entry))) result.add(text(entry));
                else collectPaintIds(entry, result);
            });
        } else if (value instanceof List<?> values) {
            values.forEach(entry -> collectPaintIds(entry, result));
        }
    }

    private static String paintableProductIdForCatalogItem(DataSnapshot snapshot, String catalogItemId) {
        for (var product : snapshot.paintableProducts()) {
            if (product.catalogItems().stream().anyMatch(item -> catalogItemId.equals(item.id()))) {
                return product.id();
            }
        }
        throw new DomainException("not_found", "Catalog item not found: " + catalogItemId);
    }

    private Workshop workshopAggregate(DataSnapshot snapshot) {
        var history = snapshot.events().stream()
                .filter(event -> Workshop.DEFAULT_ID.equals(event.aggregateId()))
                .map(EventEnvelope::event)
                .filter(WorkshopEvent.class::isInstance)
                .map(WorkshopEvent.class::cast)
                .toList();
        return Workshop.rehydrate(history);
    }

    private WorkshopItem workshopItemAggregate(DataSnapshot snapshot, String workshopItemId) {
        var history = snapshot.events().stream()
                .filter(event -> workshopItemId.equals(event.aggregateId()))
                .map(EventEnvelope::event)
                .filter(WorkshopItemEvent.class::isInstance)
                .map(WorkshopItemEvent.class::cast)
                .toList();
        if (history.isEmpty()) {
            throw new DomainException("not_found", "Workshop item not found: " + workshopItemId);
        }
        return WorkshopItem.rehydrate(history);
    }

    private PaintingProject paintingProjectAggregate(DataSnapshot snapshot, String paintingProjectId) {
        var history = snapshot.events().stream()
                .filter(event -> paintingProjectId.equals(event.aggregateId()))
                .map(EventEnvelope::event)
                .filter(PaintingProjectEvent.class::isInstance)
                .map(PaintingProjectEvent.class::cast)
                .toList();
        if (history.isEmpty()) {
            throw new DomainException("not_found", "Painting project not found: " + paintingProjectId);
        }
        return PaintingProject.rehydrate(history);
    }

    private WorkshopRecipe workshopRecipeAggregate(DataSnapshot snapshot, String recipeId) {
        var history = snapshot.events().stream()
                .filter(event -> recipeId.equals(event.aggregateId()))
                .map(EventEnvelope::event)
                .filter(WorkshopRecipeEvent.class::isInstance)
                .map(WorkshopRecipeEvent.class::cast)
                .toList();
        if (history.isEmpty()) {
            throw new DomainException("not_found", "Workshop recipe not found: " + recipeId);
        }
        return WorkshopRecipe.rehydrate(history);
    }

    private PublicationReceipt publish(
            AggregateRoot aggregate, Actor actor, String requestedCorrelationId, String idempotencyKey) {
        var recordedAt = Instant.now();
        var correlationId = defaultText(requestedCorrelationId, Ulid.next(recordedAt));
        var envelopes = envelopeFactory.envelop(
                aggregate, actor, correlationId, null, idempotencyKey, recordedAt);
        if (envelopes.isEmpty()) {
            throw new DomainException("no_change", "The command did not produce a domain event.");
        }
        return publish(envelopes, correlationId, idempotencyKey);
    }

    private PublicationReceipt publish(
            List<EventEnvelope> envelopes, String correlationId, String idempotencyKey) {
        var acceptedAt = Instant.now();
        var batch = new EventBatch(
                Ulid.next(acceptedAt), correlationId, idempotencyKey, acceptedAt, envelopes);
        return eventBus.publish(batch);
    }

    private static void validateRecipeSolutions(
            List<RecipeSolution> solutions, Map<String, Object> guide, DataSnapshot snapshot) {
        var ownedPaintIds = documentMaps(snapshot.paintInventory()).stream()
                .filter(entry -> number(entry.get("quantity")) > 0)
                .map(entry -> text(entry.get("paint_id"))).collect(Collectors.toSet());
        var guideSlotIds = listOfMaps(guide.get("slots")).stream()
                .map(slot -> text(slot.get("id"))).collect(Collectors.toSet());
        var usedSlots = new java.util.HashSet<String>();
        for (var solution : solutions) {
            var slotId = defaultText(solution.guideSlotId(), "");
            if (!guide.isEmpty()) {
                require(slotId, "solutions.guide_slot_id");
                if (!guideSlotIds.contains(slotId)) throw new DomainException("invalid_input", "Unknown market guide slot: " + slotId);
                if (!usedSlots.add(slotId)) throw new DomainException("invalid_input", "A market guide slot can only have one workshop solution: " + slotId);
            }
            var paintIds = solution.referencedPaintIds();
            var missing = paintIds.stream().filter(id -> !ownedPaintIds.contains(id)).toList();
            if (!missing.isEmpty()) {
                throw new DomainException("conflict", "Workshop recipe can only use owned paints: " + String.join(", ", missing));
            }
        }
    }

    private EventEnvelope idempotent(DataSnapshot snapshot, String key) {
        if (!present(key)) return null;
        return snapshot.events().stream().filter(event -> key.equals(event.idempotencyKey())).findFirst().orElse(null);
    }

    private static PublicationReceipt existingReceipt(EventEnvelope envelope) {
        return new PublicationReceipt(
                envelope.eventId(), EventPublicationStatus.COMPLETED,
                envelope.recordedAt(), envelope.correlationId());
    }

    private static String requiredText(String value, String field) {
        require(value, field);
        return value.trim();
    }

    private static Object camelize(Object value) {
        if (value instanceof List<?> list) return list.stream().map(MiniPaintDexService::camelize).toList();
        if (value instanceof Map<?, ?> source) {
            var result = new LinkedHashMap<String, Object>();
            source.forEach((key, entry) -> result.put(camelKey(String.valueOf(key)), camelize(entry)));
            return result;
        }
        return value;
    }

    private static SiteConfigurationView.Section configurationSection(
            Map<String, Object> values, boolean normalizeKeys) {
        return new SiteConfigurationView.Section(values.entrySet().stream()
                .map(entry -> {
                    var key = normalizeKeys ? camelKey(entry.getKey()) : entry.getKey();
                    var normalizeChildren = normalizeKeys && !Set.of(
                            "workflow", "kind_labels", "event_labels", "document_titles").contains(entry.getKey());
                    return new SiteConfigurationView.Entry(
                            key, configurationValue(entry.getValue(), normalizeChildren));
                })
                .toList());
    }

    private static SiteConfigurationView.Value configurationValue(Object value, boolean normalizeKeys) {
        if (value instanceof Map<?, ?> values) return configurationSection(map(values), normalizeKeys);
        if (value instanceof List<?> values) {
            return new SiteConfigurationView.ListValue(values.stream()
                    .map(entry -> configurationValue(entry, normalizeKeys)).toList());
        }
        if (value instanceof Number number) return new SiteConfigurationView.NumberValue(number);
        if (value instanceof Boolean bool) return new SiteConfigurationView.BooleanValue(bool);
        return new SiteConfigurationView.TextValue(text(value));
    }

    private static Map<String, Object> documentMap(StructuredDocument document) {
        var result = new LinkedHashMap<String, Object>();
        document.fields().forEach(field -> result.put(field.name(), documentValue(field.value())));
        return result;
    }

    private static List<Map<String, Object>> documentMaps(List<StructuredDocument> documents) {
        return documents.stream().map(MiniPaintDexService::documentMap).toList();
    }

    private static List<StructuredDocument> structuredDocuments(List<Map<String, Object>> documents) {
        return documents.stream().map(MiniPaintDexService::structuredDocument).toList();
    }

    private static StructuredDocument structuredDocument(Map<String, Object> document) {
        return new StructuredDocument(document.entrySet().stream()
                .map(entry -> new StructuredDocument.Field(entry.getKey(), structuredValue(entry.getValue())))
                .toList());
    }

    private static StructuredDocument.Value structuredValue(Object value) {
        if (value == null) return new StructuredDocument.NullValue();
        if (value instanceof Map<?, ?> nested) {
            return new StructuredDocument.ObjectValue(structuredDocument(map(nested)));
        }
        if (value instanceof List<?> list) {
            return new StructuredDocument.ArrayValue(list.stream()
                    .map(MiniPaintDexService::structuredValue).toList());
        }
        if (value instanceof Number number) return new StructuredDocument.NumberValue(number);
        if (value instanceof Boolean bool) return new StructuredDocument.BooleanValue(bool);
        return new StructuredDocument.Text(String.valueOf(value));
    }

    private static Object documentValue(StructuredDocument.Value value) {
        return switch (value) {
            case StructuredDocument.Text text -> text.value();
            case StructuredDocument.NumberValue number -> number.value();
            case StructuredDocument.BooleanValue bool -> bool.value();
            case StructuredDocument.NullValue ignored -> null;
            case StructuredDocument.ArrayValue array -> array.values().stream()
                    .map(MiniPaintDexService::documentValue).toList();
            case StructuredDocument.ObjectValue object -> documentMap(object.value());
        };
    }

    private static String camelKey(String value) {
        // Dotted values are domain identifiers (for example event types), not structural YAML keys.
        if (value.contains(".")) return value;
        var result = new StringBuilder();
        var upper = false;
        for (var character : value.toCharArray()) {
            if (character == '_') { upper = true; continue; }
            result.append(upper ? Character.toUpperCase(character) : character);
            upper = false;
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(MiniPaintDexService::map).toList();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(MiniPaintDexService::text).filter(MiniPaintDexService::present).toList();
    }

    private static int size(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value)); } catch (NumberFormatException ignored) { return 0; }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static void validateMarketPaint(Map<String, Object> paint) {
        for (var field : List.of("id", "brand", "manufacturer", "range", "functional_type", "name")) {
            require(text(paint.get(field)), field);
        }
        if (!text(paint.get("id")).matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new DomainException("invalid_input", "Paint id must use lowercase kebab-case.");
        }
        if (TECHNICAL_PAINT_TYPES.contains(text(paint.get("functional_type")))) {
            var instructions = map(paint.get("usage_instructions"));
            if (!present(text(instructions.get("summary"))) || strings(instructions.get("steps")).isEmpty()) {
                throw new DomainException("invalid_input", "Technical paint requires usage_instructions.summary and steps.");
            }
        }
    }

    private static String defaultText(String value, String fallback) {
        return present(value) ? value : fallback;
    }

    private static void require(String value, String field) {
        if (!present(value)) throw new DomainException("invalid_input", field + " is required.");
    }

}
