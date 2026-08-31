package com.minipaintdex.application;

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
import com.minipaintdex.application.command.SetShoppingItemStatusCommand;
import com.minipaintdex.application.command.ReplaceWorkshopPaintInventoryCommand;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventLedger;
import com.minipaintdex.application.port.MarketPaintCatalogWriter;
import com.minipaintdex.application.port.PaintableProductCatalogWriter;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.WorkshopPaintInventoryWriter;
import com.minipaintdex.application.port.WorkshopMediaStorage;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.result.ApplyMarketPaintChangeSetResult;
import com.minipaintdex.application.result.ApplyMarketPaintableProductChangeSetResult;
import com.minipaintdex.application.result.CreatePaintingProjectResult;
import com.minipaintdex.application.result.ReplaceWorkshopPaintInventoryResult;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.paint.PaintMatchEngine;
import com.minipaintdex.domain.product.PaintableProduct;
import com.minipaintdex.domain.workflow.DomainException;
import com.minipaintdex.domain.workflow.StageAction;
import com.minipaintdex.domain.workflow.WorkflowStage;
import com.minipaintdex.domain.workflow.WorkflowStageStatus;
import com.minipaintdex.domain.workshop.WorkshopItemProjector;
import com.minipaintdex.domain.workshop.WorkshopItemState;
import com.minipaintdex.domain.workshop.PaintingProjectProjector;
import com.minipaintdex.domain.workshop.PaintingProject;
import com.minipaintdex.domain.workshop.WorkshopRecipeProjector;
import com.minipaintdex.domain.workshop.WorkshopRecipeState;
import com.minipaintdex.domain.workshop.WorkshopRecipeStatus;
import com.minipaintdex.domain.workshop.Workshop;
import com.minipaintdex.domain.workshop.WorkshopProjector;

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

public final class MiniPaintDexService {
    private static final Set<String> TECHNICAL_PAINT_TYPES = Set.of(
            "technical_effect", "primer", "wash_shade", "ink", "auxiliary");
    private static final Set<String> RECIPE_SOLUTION_TYPES = Set.of(
            "single_paint", "mixture", "layer_stack", "technique");
    private final PaintMatchEngine paintMatchEngine;
    private final SnapshotRepository snapshots;
    private final EventLedger ledger;
    private final MarketPaintCatalogWriter marketPaints;
    private final WorkshopPaintInventoryWriter workshopPaints;
    private final PaintableProductCatalogWriter paintableProducts;
    private final WorkshopMediaStorage mediaStorage;
    private final MarketPaintQueryService paintQueries;
    private final WorkshopMediaPolicy mediaPolicy;

    public MiniPaintDexService(
            SnapshotRepository snapshots,
            EventLedger ledger,
            MarketPaintCatalogWriter marketPaints,
            WorkshopPaintInventoryWriter workshopPaints,
            PaintableProductCatalogWriter paintableProducts,
            WorkshopMediaStorage mediaStorage,
            WorkshopMediaPolicy mediaPolicy,
            PaintMatchEngine paintMatchEngine) {
        this.snapshots = Objects.requireNonNull(snapshots);
        this.ledger = Objects.requireNonNull(ledger);
        this.marketPaints = Objects.requireNonNull(marketPaints);
        this.workshopPaints = Objects.requireNonNull(workshopPaints);
        this.paintableProducts = Objects.requireNonNull(paintableProducts);
        this.mediaStorage = Objects.requireNonNull(mediaStorage);
        this.mediaPolicy = Objects.requireNonNull(mediaPolicy);
        this.paintQueries = new MarketPaintQueryService(snapshots);
        this.paintMatchEngine = Objects.requireNonNull(paintMatchEngine);
    }

    public Map<String, Object> bootstrap() {
        return project(snapshots.load());
    }

    public Map<String, Object> bootstrap(boolean includeMarketPaints) {
        var result = new LinkedHashMap<>(project(snapshots.load()));
        if (!includeMarketPaints) result.put("paints", List.of());
        return java.util.Collections.unmodifiableMap(result);
    }

    public Map<String, Object> siteConfiguration() {
        return map(camelize(snapshots.load().site()));
    }

    public Map<String, Object> health() {
        var snapshot = snapshots.load();
        return Map.of(
                "status", "ok",
                "service", "minipaintdex",
                "storage", "files",
                "marketPaints", snapshot.marketPaints().size(),
                "events", snapshot.events().size());
    }

    public List<Map<String, Object>> searchMarketPaints(SearchMarketPaintsQuery filters) {
        return paintQueries.search(filters);
    }

    public Map<String, Object> searchMarketPaintPage(
            SearchMarketPaintsQuery filters,
            boolean ownedOnly,
            boolean manufacturerSheetOnly,
            boolean realResultOnly,
            int offset,
            int limit) {
        return paintQueries.page(filters, ownedOnly, manufacturerSheetOnly, realResultOnly, offset, limit);
    }

    public Map<String, Object> marketPaintFacets(boolean ownedOnly) {
        return paintQueries.facets(ownedOnly);
    }

    public Map<String, Object> getMarketPaint(String id) {
        return searchMarketPaints(SearchMarketPaintsQuery.empty()).stream().filter(paint -> id.equals(paint.get("id"))).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Paint not found: " + id));
    }

    public ApplyMarketPaintChangeSetResult applyMarketPaintChangeSet(ApplyMarketPaintChangeSetCommand command) {
        if (command.schemaVersion() != 1) throw new DomainException("invalid_input", "schemaVersion must be 1.");
        if (!"market_paints".equals(command.kind())) throw new DomainException("invalid_input", "kind must be market_paints.");
        if (command.operations().isEmpty()) throw new DomainException("invalid_input", "At least one operation is required.");
        var snapshot = snapshots.load();
        var current = snapshot.marketPaints();
        var byId = new LinkedHashMap<String, Map<String, Object>>();
        current.forEach(paint -> byId.put(text(paint.get("id")), paint));
        var added = 0;
        var updated = 0;
        var retired = 0;
        var deleted = 0;
        var unchanged = 0;
        var inventoryChanged = 0;
        var quantities = new LinkedHashMap<String, Integer>();
        snapshot.paintInventory().forEach(entry -> quantities.merge(
                text(entry.get("paint_id")), number(entry.get("quantity")), Integer::sum));
        var referencedPaintIds = referencedPaintIds(snapshot);
        for (var operation : command.operations()) {
            var id = text(operation.record().get("id"));
            require(id, "record.id");
            if ("upsert".equals(operation.action())) {
                validateMarketPaint(operation.record());
                var previous = byId.put(id, new LinkedHashMap<>(operation.record()));
                if (previous == null) added++;
                else if (previous.equals(operation.record())) unchanged++;
                else updated++;
            } else if ("retire".equals(operation.action())) {
                var previous = byId.get(id);
                if (previous == null) throw new DomainException("not_found", "Paint not found: " + id);
                var replacement = new LinkedHashMap<>(previous);
                replacement.put("lifecycle_status", defaultText(text(operation.record().get("lifecycle_status")), "discontinued"));
                if (present(text(operation.record().get("verified_at")))) replacement.put("verified_at", operation.record().get("verified_at"));
                if (present(text(operation.record().get("removal_reason")))) replacement.put("removal_reason", operation.record().get("removal_reason"));
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
                marketPaints.replaceMarketPaintsAndWorkshopInventory(result, inventory, workshopPaints);
            } else {
                marketPaints.replaceMarketPaints(result);
            }
        }
        return new ApplyMarketPaintChangeSetResult(
                added, updated, retired, deleted, unchanged, inventoryChanged, result.size(), !command.dryRun());
    }

    public ReplaceWorkshopPaintInventoryResult replaceWorkshopPaintInventory(
            ReplaceWorkshopPaintInventoryCommand command) {
        if (command.schemaVersion() != 1) {
            throw new DomainException("invalid_input", "schemaVersion must be 1.");
        }
        if (!"workshop_paints".equals(command.kind())) {
            throw new DomainException("invalid_input", "kind must be workshop_paints.");
        }
        var marketIds = snapshots.load().marketPaints().stream()
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
        if (!command.dryRun()) workshopPaints.replaceWorkshopPaints(normalized);
        return new ReplaceWorkshopPaintInventoryResult(
                normalized.size(), inventory.values().stream().mapToInt(Integer::intValue).sum(), !command.dryRun());
    }

    public ApplyMarketPaintableProductChangeSetResult applyMarketPaintableProductChangeSet(ApplyMarketPaintableProductChangeSetCommand command) {
        if (command.schemaVersion() != 1) throw new DomainException("invalid_input", "schemaVersion must be 1.");
        if (!"market_product".equals(command.kind())) throw new DomainException("invalid_input", "kind must be market_product.");
        var product = product(command.product());
        var catalogItemIds = product.catalogItems().stream().map(PaintableProduct.CatalogItem::id).collect(Collectors.toSet());
        var marketPaintIds = snapshots.load().marketPaints().stream().map(paint -> text(paint.get("id"))).collect(Collectors.toSet());
        for (var guide : command.paintingGuides()) {
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
            paintableProducts.replaceProduct(product.id(), command.product(), command.paintingGuides());
        }
        return new ApplyMarketPaintableProductChangeSetResult(
                product.id(), product.catalogItems().size(), command.paintingGuides().size(), !command.dryRun());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listMarketPaintableProducts() {
        return (List<Map<String, Object>>) bootstrap().get("marketPaintableProducts");
    }

    public Map<String, Object> getMarketPaintableProduct(String productId) {
        return listMarketPaintableProducts().stream().filter(product -> productId.equals(product.get("id"))).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Paintable product not found: " + productId));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> workshopOverview() {
        return (Map<String, Object>) bootstrap().get("workshop");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listPaintingProjects() {
        return (List<Map<String, Object>>) workshopOverview().get("paintingProjects");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listWorkshopItems(String paintingProjectId) {
        var items = (List<Map<String, Object>>) bootstrap().get("workshopItems");
        if (!present(paintingProjectId)) return items;
        return items.stream().filter(item -> paintingProjectId.equals(item.get("paintingProjectId"))).toList();
    }

    public Map<String, Object> getWorkshopItem(String itemId) {
        require(itemId, "itemId");
        var snapshot = snapshots.load();
        var item = WorkshopItemProjector.project(snapshot.events()).stream()
                .filter(candidate -> itemId.equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop item not found: " + itemId));
        var result = new LinkedHashMap<>(workshopItemView(item));
        result.put("activity", snapshot.events().stream()
                .filter(event -> itemId.equals(event.aggregateId()))
                .sorted(Comparator.comparing(DomainEvent::recordedAt).reversed()).toList());
        return java.util.Collections.unmodifiableMap(result);
    }

    public Map<String, Object> previewProductImport(String productId) {
        var snapshot = snapshots.load();
        var product = findProduct(snapshot, productId);
        return importPreview(snapshot, product);
    }

    private Map<String, Object> importPreview(DataSnapshot snapshot, PaintableProduct product) {
        var catalogIds = product.catalogItems().stream().map(PaintableProduct.CatalogItem::id).collect(Collectors.toSet());
        var guides = snapshot.marketPaintingGuides().stream()
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
        var ownedPaintIds = snapshot.paintInventory().stream()
                .filter(entry -> number(entry.get("quantity")) > 0)
                .map(entry -> text(entry.get("paint_id"))).collect(Collectors.toSet());
        var paintsById = snapshot.marketPaints().stream()
                .collect(Collectors.toMap(paint -> text(paint.get("id")), Function.identity()));
        var missingPaints = requiredPaintIds.stream().filter(id -> !ownedPaintIds.contains(id)).map(id -> {
            var paint = paintsById.getOrDefault(id, Map.of());
            return Map.<String, Object>of(
                    "id", id,
                    "name", text(paint.get("name")),
                    "brand", text(paint.get("brand")),
                    "reference", text(paint.get("reference")));
        }).toList();
        var paintingProjects = PaintingProjectProjector.project(snapshot.events());
        var result = new LinkedHashMap<String, Object>();
        result.put("productId", product.id());
        result.put("productName", product.name());
        result.put("catalogItemCount", product.catalogItems().size());
        result.put("paintableItemCount", product.expectedPaintableCount());
        result.put("paintingGuideCount", guides.size());
        result.put("requiredPaintCount", requiredPaintIds.size());
        result.put("missingPaintCount", missingPaints.size());
        result.put("missingPaints", missingPaints);
        result.put("pendingPaintSlotCount", pendingSlots);
        result.put("alreadyImported", paintingProjects.stream()
                .anyMatch(project -> product.id().equals(project.paintableProductId())));
        return Map.copyOf(result);
    }

    public CreatePaintingProjectResult createPaintingProject(CreatePaintingProjectCommand command) {
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
                    Workshop.DEFAULT_ID, existingProject.id(), product.id(), 0, existingForProduct, true, false);
        }

        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var recordedAt = Instant.now();
        var correlationId = defaultText(command.correlationId(), Ulid.next(recordedAt));
        var actor = new Actor("user", defaultText(command.actorId(), "owner"));
        var baseKey = defaultText(command.idempotencyKey(), "create-painting-project:" + paintingProjectId);
        var events = new ArrayList<DomainEvent>();
        if (snapshot.events().stream().noneMatch(event -> "workshop".equals(event.aggregateType()))) {
            events.add(new DomainEvent(Ulid.next(recordedAt), 1, "workshop.created", occurredAt, recordedAt,
                    "workshop", Workshop.DEFAULT_ID, null, actor, correlationId, null,
                    baseKey + ":workshop", Map.of("name", "My workshop")));
        }
        events.add(new DomainEvent(Ulid.next(recordedAt), 1, "painting_project.created", occurredAt, recordedAt,
                "painting_project", paintingProjectId, paintingProjectId, actor, correlationId, null, baseKey,
                Map.of("workshop_id", Workshop.DEFAULT_ID, "paintable_product_id", product.id(), "name", paintingProjectName,
                        "paintable_item_count", product.expectedPaintableCount())));

        var existingIds = existingItems.stream().map(WorkshopItemState::id).collect(Collectors.toSet());
        var added = 0;
        for (var catalogItem : product.catalogItems()) {
            for (var ordinal = 1; ordinal <= catalogItem.quantity(); ordinal++) {
                var itemId = "ws-" + catalogItem.id() + "-" + String.format(Locale.ROOT, "%03d", ordinal);
                if (existingIds.contains(itemId)) continue;
                var displayName = catalogItem.quantity() == 1
                        ? catalogItem.name()
                        : catalogItem.name() + " #" + ordinal;
                events.add(new DomainEvent(Ulid.next(recordedAt), 1, "workshop_item.added", occurredAt, recordedAt,
                        "workshop_item", itemId, paintingProjectId, actor, correlationId, null, baseKey + ":" + itemId,
                        Map.of("catalog_item_id", catalogItem.id(), "painting_project_id", paintingProjectId,
                                "display_name", displayName, "ordinal", ordinal)));
                added++;
            }
        }
        ledger.appendAll(events);
        return new CreatePaintingProjectResult(
                Workshop.DEFAULT_ID, paintingProjectId, product.id(), added, existingForProduct, false, true);
    }

    public List<Map<String, Object>> listMarketPaintingGuides(String catalogItemId) {
        return snapshots.load().marketPaintingGuides().stream()
                .filter(guide -> !present(catalogItemId) || catalogItemId.equals(text(guide.get("catalog_item_id"))))
                .map(guide -> map(camelize(guide)))
                .sorted(Comparator.comparing(guide -> text(guide.get("id"))))
                .toList();
    }

    public Map<String, Object> reconcileMarketPaintingGuide(String guideId) {
        require(guideId, "guideId");
        var snapshot = snapshots.load();
        var guide = snapshot.marketPaintingGuides().stream()
                .filter(candidate -> guideId.equals(text(candidate.get("id"))))
                .findFirst().orElseThrow(() -> new DomainException("not_found", "Market painting guide not found: " + guideId));
        var paintsById = snapshot.marketPaints().stream()
                .collect(Collectors.toMap(paint -> text(paint.get("id")), Function.identity()));
        var ownedIds = snapshot.paintInventory().stream()
                .filter(entry -> number(entry.get("quantity")) > 0)
                .map(entry -> text(entry.get("paint_id"))).collect(Collectors.toSet());
        var ownedProfiles = ownedIds.stream().map(paintsById::get).filter(Objects::nonNull)
                .map(this::paintProfile).toList();
        var slots = listOfMaps(guide.get("slots")).stream().map(slot -> {
            var sourcePaintId = text(slot.get("market_paint_id"));
            var sourcePaint = paintsById.get(sourcePaintId);
            var candidates = sourcePaint == null ? List.<Map<String, Object>>of() : paintMatchEngine
                    .rank(paintProfile(sourcePaint), ownedProfiles).stream()
                    .map(match -> {
                        var view = new LinkedHashMap<String, Object>();
                        view.put("paint", map(camelize(paintsById.get(match.candidatePaintId()))));
                        view.put("score", match.score());
                        view.put("deltaE2000", match.deltaE2000());
                        view.put("requiresManualReview", match.requiresManualReview());
                        view.put("strategy", match.strategy());
                        view.put("dimensions", Map.of(
                                "color", match.colorScore(), "functionalType", match.functionalTypeScore(),
                                "behavior", match.behaviorScore(), "finish", match.finishScore(),
                                "opacity", match.opacityScore(), "medium", match.mediumScore()));
                        view.put("reasons", match.reasons());
                        return Map.copyOf(view);
                    }).toList();
            var result = new LinkedHashMap<String, Object>();
            result.put("slot", map(camelize(slot)));
            if (sourcePaint != null) result.put("sourcePaint", map(camelize(sourcePaint)));
            result.put("candidates", candidates);
            result.put("requiresManualReview", sourcePaint != null
                    && paintMatchEngine.requiresManualReview(paintProfile(sourcePaint)));
            return Map.copyOf(result);
        }).toList();
        return Map.of("guide", map(camelize(guide)), "slots", slots, "ownedPaintCount", ownedProfiles.size());
    }

    public List<Map<String, Object>> listWorkshopRecipes(String catalogItemId) {
        return WorkshopRecipeProjector.project(snapshots.load().events()).stream()
                .filter(recipe -> !present(catalogItemId) || catalogItemId.equals(recipe.catalogItemId()))
                .map(this::workshopRecipeView)
                .sorted(Comparator.comparing(recipe -> text(recipe.get("id"))))
                .toList();
    }

    public DomainEvent createWorkshopRecipe(CreateWorkshopRecipeCommand command) {
        require(command.catalogItemId(), "catalogItemId");
        require(command.displayName(), "displayName");
        if (command.version() < 1) throw new DomainException("invalid_input", "version must be positive.");
        if (command.solutions().isEmpty()) throw new DomainException("invalid_input", "At least one recipe solution is required.");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return duplicate;
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
            guide = snapshot.marketPaintingGuides().stream()
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
        var payload = new LinkedHashMap<String, Object>();
        payload.put("catalog_item_id", command.catalogItemId());
        payload.put("display_name", command.displayName());
        payload.put("version", command.version());
        payload.put("solutions", command.solutions());
        payload.put("painting_project_id", paintingProject.id());
        if (present(command.basedOnGuideId())) payload.put("based_on_guide_id", command.basedOnGuideId());
        if (present(command.supersedesRecipeId())) payload.put("supersedes_recipe_id", command.supersedesRecipeId());
        var event = new DomainEvent(Ulid.next(occurredAt), 1, "workshop_recipe.created", occurredAt, Instant.now(),
                "workshop_recipe", recipeId, paintingProject.id(), new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(), payload);
        return ledger.append(event);
    }

    public DomainEvent transitionWorkshopRecipe(TransitionWorkshopRecipeCommand command) {
        require(command.recipeId(), "recipeId");
        require(command.action(), "action");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return duplicate;
        var recipe = WorkshopRecipeProjector.project(snapshot.events()).stream()
                .filter(candidate -> command.recipeId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop recipe not found: " + command.recipeId()));
        WorkshopRecipeProjector.assertTransition(recipe.status(), command.action());
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var payload = new LinkedHashMap<String, Object>();
        if (present(command.comment())) payload.put("comment", command.comment());
        var paintingProject = paintingProjectForProduct(
                snapshot, paintableProductIdForCatalogItem(snapshot, recipe.catalogItemId()));
        payload.put("painting_project_id", paintingProject.id());
        var event = new DomainEvent(Ulid.next(occurredAt), 1, WorkshopRecipeProjector.eventType(command.action()),
                occurredAt, Instant.now(), "workshop_recipe", recipe.id(), paintingProject.id(),
                new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(), payload);
        return ledger.append(event);
    }

    public DomainEvent assignWorkshopRecipe(AssignWorkshopRecipeCommand command) {
        require(command.itemId(), "itemId");
        require(command.recipeId(), "recipeId");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return duplicate;
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
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var event = new DomainEvent(Ulid.next(occurredAt), 1, "recipe.assigned", occurredAt, Instant.now(),
                "workshop_item", item.id(), item.paintingProjectId(), new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(),
                Map.of("recipe_id", recipe.id(), "recipe_version", recipe.version(),
                        "painting_project_id", item.paintingProjectId()));
        return ledger.append(event);
    }

    public List<DomainEvent> listActivity(String paintingProjectId) {
        var snapshot = snapshots.load();
        var itemIds = WorkshopItemProjector.project(snapshot.events()).stream()
                .filter(item -> !present(paintingProjectId) || paintingProjectId.equals(item.paintingProjectId()))
                .map(WorkshopItemState::id).collect(Collectors.toSet());
        return snapshot.events().stream()
                .filter(event -> !present(paintingProjectId)
                        || paintingProjectId.equals(event.projectId())
                        || paintingProjectId.equals(text(event.payload().get("painting_project_id")))
                        || itemIds.contains(event.aggregateId()))
                .sorted(Comparator.comparing(DomainEvent::recordedAt).reversed())
                .toList();
    }

    public DomainEvent addWorkshopItem(AddWorkshopItemCommand command) {
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
        if (duplicate != null) return duplicate;
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var itemId = present(command.itemId()) ? command.itemId() : "ws-" + command.catalogItemId() + "-" + Ulid.next(occurredAt).toLowerCase(Locale.ROOT);
        if (snapshot.events().stream().anyMatch(event -> itemId.equals(event.aggregateId()) && "workshop_item.added".equals(event.eventType()))) {
            throw new DomainException("conflict", "Workshop item already exists: " + itemId);
        }
        var event = new DomainEvent(Ulid.next(occurredAt), 1, "workshop_item.added", occurredAt, Instant.now(),
                "workshop_item", itemId, paintingProject.id(), new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(),
                Map.of("catalog_item_id", command.catalogItemId(), "painting_project_id", paintingProject.id(),
                        "display_name", command.displayName()));
        return ledger.append(event);
    }

    public DomainEvent transitionStage(TransitionStageCommand command) {
        require(command.itemId(), "itemId");
        var stage = WorkflowStage.fromId(command.stage());
        var action = StageAction.fromId(command.action());
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return duplicate;
        var item = WorkshopItemProjector.project(snapshot.events()).stream().filter(candidate -> command.itemId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop item not found: " + command.itemId()));
        WorkshopItemProjector.assertTransition(item.workflow(), stage, action);
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("stage", stage.id());
        payload.put("painting_project_id", item.paintingProjectId());
        if (present(command.comment())) payload.put("comment", command.comment());
        if (present(command.reason())) payload.put("reason", command.reason());
        var event = new DomainEvent(Ulid.next(occurredAt), 1, action.eventType(), occurredAt, Instant.now(),
                "workshop_item", item.id(), item.paintingProjectId(), new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(), payload);
        return ledger.append(event);
    }

    public DomainEvent addWorkshopItemComment(AddWorkshopItemCommentCommand command) {
        require(command.itemId(), "itemId");
        require(command.comment(), "comment");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return duplicate;
        var item = WorkshopItemProjector.project(snapshot.events()).stream()
                .filter(candidate -> command.itemId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop item not found: " + command.itemId()));
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var event = new DomainEvent(Ulid.next(occurredAt), 1, "workshop_item.comment_added",
                occurredAt, Instant.now(), "workshop_item", item.id(), item.paintingProjectId(),
                new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(),
                Map.of("comment", command.comment().trim(), "painting_project_id", item.paintingProjectId()));
        return ledger.append(event);
    }

    public DomainEvent addWorkshopItemPhoto(AddWorkshopItemPhotoCommand command) {
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
        if (duplicate != null) return duplicate;
        var item = WorkshopItemProjector.project(snapshot.events()).stream()
                .filter(candidate -> command.itemId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop item not found: " + command.itemId()));
        if (present(command.stage())) WorkflowStage.fromId(command.stage());
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var mediaId = "media-" + Ulid.next(occurredAt).toLowerCase(Locale.ROOT);
        var stored = mediaStorage.store(item.id(), mediaId, command.originalFilename(), contentType, content);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("media_id", stored.id());
        payload.put("url", stored.publicPath());
        payload.put("storage_path", stored.storagePath());
        payload.put("original_filename", stored.originalFilename());
        payload.put("content_type", stored.contentType());
        payload.put("size", stored.size());
        payload.put("sha256", stored.sha256());
        payload.put("painting_project_id", item.paintingProjectId());
        if (present(command.stage())) payload.put("stage", command.stage());
        if (present(command.caption())) payload.put("caption", command.caption().trim());
        var event = new DomainEvent(Ulid.next(Instant.now()), 1, "workshop_item.photo_added",
                occurredAt, Instant.now(), "workshop_item", item.id(), item.paintingProjectId(),
                new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(), payload);
        try {
            return ledger.append(event);
        } catch (RuntimeException failure) {
            mediaStorage.delete(stored);
            throw failure;
        }
    }

    public DomainEvent setShoppingItemStatus(SetShoppingItemStatusCommand command) {
        require(command.itemId(), "itemId");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return duplicate;
        var known = shoppingViews(snapshot).stream().anyMatch(item -> command.itemId().equals(item.get("id")));
        if (!known) throw new DomainException("not_found", "Shopping item not found: " + command.itemId());
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var event = new DomainEvent(Ulid.next(occurredAt), 1, "shopping_item.status_changed",
                occurredAt, Instant.now(), "shopping_item", command.itemId(), null,
                new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(),
                Map.of("checked", command.checked()));
        return ledger.append(event);
    }

    public Map<String, Object> rebuildProjections() {
        var snapshot = snapshots.load();
        var view = project(snapshot);
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "rebuilt");
        result.put("storage", "in_memory");
        result.put("eventCount", snapshot.events().size());
        result.put("rebuiltAt", Instant.now());
        result.putAll(Map.of(
                "paints", size(view.get("paints")),
                "marketPaintableProducts", size(view.get("marketPaintableProducts")),
                "paintingProjects", size(((Map<?, ?>) view.get("workshop")).get("paintingProjects")),
                "workshopItems", size(view.get("workshopItems")),
                "marketPaintingGuides", size(view.get("marketPaintingGuides")),
                "workshopRecipes", size(view.get("workshopRecipes"))));
        return Map.copyOf(result);
    }

    public String exportPaints(String format) {
        var paints = searchMarketPaints(SearchMarketPaintsQuery.empty());
        if ("csv".equals(format)) {
            var rows = new ArrayList<String>();
            rows.add("id,brand,range,reference,name,color_hex,color_family,finish,medium,volume_ml,quantity");
            for (var paint : paints) rows.add(List.of("id", "brand", "range", "reference", "name", "colorHex", "colorFamily", "finish", "medium", "volumeMl", "quantity").stream().map(key -> csv(paint.get(key))).collect(Collectors.joining(",")));
            return String.join("\n", rows) + "\n";
        }
        if ("yaml".equals(format)) {
            var output = new StringBuilder("paints:\n");
            for (var paint : paints) {
                output.append("  - id: ").append(quoted(paint.get("id"))).append('\n');
                output.append("    brand: ").append(quoted(paint.get("brand"))).append('\n');
                output.append("    range: ").append(quoted(paint.get("range"))).append('\n');
                output.append("    reference: ").append(quoted(paint.get("reference"))).append('\n');
                output.append("    name: ").append(quoted(paint.get("name"))).append('\n');
                output.append("    quantity: ").append(number(paint.get("quantity"))).append('\n');
            }
            return output.toString();
        }
        throw new DomainException("not_found", "Unknown export format: " + format);
    }

    private Map<String, Object> project(DataSnapshot snapshot) {
        var items = WorkshopItemProjector.project(snapshot.events());
        var workshopRecipes = WorkshopRecipeProjector.project(snapshot.events());
        var paints = paintViews(snapshot);
        var marketProducts = paintableProductViews(snapshot, paints);
        var result = new LinkedHashMap<String, Object>();
        result.put("paints", paints);
        result.put("paintStats", Map.of(
                "total", paints.size(),
                "owned", paints.stream().filter(paint -> number(paint.get("quantity")) > 0).count(),
                "brands", paints.stream().map(paint -> text(paint.get("brand"))).filter(MiniPaintDexService::present).distinct().count()));
        result.put("workshopPaints", paints.stream().filter(paint -> number(paint.get("quantity")) > 0).toList());
        result.put("marketPaintableProducts", marketProducts);
        result.put("workshop", workshopView(snapshot, marketProducts, items));
        result.put("workshopItems", items.stream().map(this::workshopItemView).toList());
        result.put("marketPaintingGuides", snapshot.marketPaintingGuides().stream().map(MiniPaintDexService::camelize).toList());
        result.put("workshopRecipes", workshopRecipes.stream().map(this::workshopRecipeView).toList());
        result.put("shoppingSeed", shoppingViews(snapshot));
        result.put("config", camelize(snapshot.site()));
        return result;
    }

    private List<Map<String, Object>> paintViews(DataSnapshot snapshot) {
        return paintQueries.views(snapshot);
    }

    private List<Map<String, Object>> paintableProductViews(DataSnapshot snapshot, List<Map<String, Object>> paints) {
        var guides = snapshot.marketPaintingGuides().stream().collect(Collectors.toMap(
                entry -> text(entry.get("catalog_item_id")), Function.identity(),
                (left, right) -> number(left.get("version")) >= number(right.get("version")) ? left : right));
        var paintsById = paints.stream().collect(Collectors.toMap(entry -> text(entry.get("id")), Function.identity()));
        var paintingProjects = PaintingProjectProjector.project(snapshot.events());
        return snapshot.paintableProducts().stream().map(product -> {
            var productItems = product.catalogItems().stream().map(item -> {
                var catalogItemId = item.id();
                var guide = guides.getOrDefault(catalogItemId, Map.of());
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("id", catalogItemId);
                view.put("productId", product.id());
                view.put("name", item.name());
                view.put("kind", item.kind());
                view.put("quantity", item.quantity());
                view.put("description", item.description());
                view.put("assemblyRequired", item.assemblyRequired());
                view.put("referenceImages", item.referenceImages().stream().map(this::imageView).toList());
                view.put("paints", listOfMaps(guide.get("slots")).stream().map(slot -> guideSlotView(slot, paintsById)).toList());
                view.put("preparation", listOfMaps(guide.get("preparation")).stream().map(this::stepView).toList());
                view.put("painting", listOfMaps(guide.get("painting")).stream().map(this::stepView).toList());
                view.put("marketGuide", guide.isEmpty() ? Map.of() : Map.of(
                        "id", text(guide.get("id")), "version", number(guide.get("version")),
                        "knowledgeStatus", text(guide.get("knowledge_status")),
                        "sources", listOfMaps(guide.get("sources")).stream().map(this::sourceView).toList()));
                view.put("sources", item.sources().stream().map(this::sourceView).toList());
                return view;
            }).toList();
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("schemaVersion", product.schemaVersion());
            view.put("id", product.id());
            view.put("name", product.name());
            view.put("line", product.line());
            view.put("productType", product.productType());
            view.put("scope", product.scope());
            view.put("expectedPaintableCount", product.expectedPaintableCount());
            view.put("edition", Map.of("note", product.edition().note(), "url", product.edition().url()));
            view.put("sources", product.sources().stream().map(this::sourceView).toList());
            view.put("items", productItems);
            view.put("inWorkshop", paintingProjects.stream()
                    .anyMatch(project -> product.id().equals(project.paintableProductId())));
            return view;
        }).sorted(Comparator.comparing(entry -> text(entry.get("name")), String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private Map<String, Object> workshopView(
            DataSnapshot snapshot,
            List<Map<String, Object>> marketProducts,
            List<WorkshopItemState> workshopItems) {
        var workshop = WorkshopProjector.project(snapshot.events());
        var paintingProjects = PaintingProjectProjector.project(snapshot.events());
        var marketById = marketProducts.stream().collect(Collectors.toMap(
                product -> text(product.get("id")), Function.identity()));
        var projects = paintingProjects.stream().map(project -> {
            var market = marketById.get(project.paintableProductId());
            if (market == null) return Map.<String, Object>of(
                    "projectId", project.id(), "productId", project.paintableProductId(),
                    "name", project.name(), "status", project.status().id(), "orphaned", true);
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
            var view = new LinkedHashMap<String, Object>();
            view.put("projectId", project.id());
            view.put("productId", project.paintableProductId());
            view.put("name", market.get("name"));
            view.put("status", project.status().id());
            view.put("createdAt", project.createdAt());
            view.put("updatedAt", project.updatedAt());
            view.put("importedAt", project.createdAt());
            view.put("itemCount", items.size());
            view.put("completedCount", completed);
            view.put("inProgressCount", inProgress);
            view.put("pendingCount", items.size() - completed - inProgress);
            view.put("progressPercentage", progress);
            view.put("missingPaintCount", preview.get("missingPaintCount"));
            view.put("missingPaints", preview.get("missingPaints"));
            view.put("requiredPaintCount", preview.get("requiredPaintCount"));
            view.put("pendingPaintSlotCount", preview.get("pendingPaintSlotCount"));
            return Map.copyOf(view);
        }).toList();
        var completedItems = (int) workshopItems.stream().filter(WorkshopItemState::completed).count();
        var result = new LinkedHashMap<String, Object>();
        result.put("id", workshop.id());
        result.put("paintingProjects", projects);
        result.put("projectCount", projects.size());
        result.put("itemCount", workshopItems.size());
        result.put("completedItemCount", completedItems);
        result.put("progressPercentage", workshopItems.isEmpty() ? 0 : Math.round(completedItems * 100f / workshopItems.size()));
        result.put("recentActivity", snapshot.events().stream()
                .sorted(Comparator.comparing(DomainEvent::recordedAt).reversed()).limit(12).toList());
        return Map.copyOf(result);
    }

    private Map<String, Object> guideSlotView(Map<String, Object> slot, Map<String, Map<String, Object>> paintsById) {
        var paint = paintsById.get(text(slot.get("market_paint_id")));
        var requested = map(slot.get("requested_paint"));
        var view = new LinkedHashMap<String, Object>();
        view.put("slotId", text(slot.get("id")));
        if (paint != null) view.put("paintId", paint.get("id"));
        view.put("brand", paint == null ? text(requested.get("brand")) : paint.get("brand"));
        view.put("name", paint == null ? text(requested.get("name")) : paint.get("name"));
        view.put("role", text(slot.get("role")));
        view.put("colorHex", paint == null ? text(requested.get("color_hex")) : paint.get("colorHex"));
        if (Boolean.TRUE.equals(slot.get("pending_import"))) view.put("pendingImport", true);
        return view;
    }

    private Map<String, Object> workshopItemView(WorkshopItemState item) {
        var workflow = new LinkedHashMap<String, Object>();
        for (var stage : WorkflowStage.values()) workflow.put(stage.id(), item.workflow().get(stage).id());
        var view = new LinkedHashMap<String, Object>();
        view.put("id", item.id());
        view.put("catalogItemId", item.catalogItemId());
        view.put("paintingProjectId", item.paintingProjectId());
        view.put("displayName", item.displayName());
        view.put("workflow", workflow);
        view.put("currentStage", item.currentStage() == null ? null : item.currentStage().id());
        view.put("completed", item.completed());
        view.put("recipeId", defaultText(item.recipeId(), ""));
        view.put("recipeVersion", item.recipeVersion());
        view.put("updatedAt", item.updatedAt());
        return view;
    }

    private List<Map<String, Object>> shoppingViews(DataSnapshot snapshot) {
        var paintsById = snapshot.marketPaints().stream().collect(Collectors.toMap(
                paint -> text(paint.get("id")), Function.identity(), (left, right) -> left));
        var productsById = snapshot.paintableProducts().stream().collect(Collectors.toMap(
                PaintableProduct::id, PaintableProduct::name));
        var requiredByPaint = new LinkedHashMap<String, java.util.LinkedHashSet<String>>();
        var paintingProjects = PaintingProjectProjector.project(snapshot.events());
        for (var paintingProject : paintingProjects) {
            var product = snapshot.paintableProducts().stream()
                    .filter(candidate -> paintingProject.paintableProductId().equals(candidate.id())).findFirst().orElse(null);
            if (product == null) continue;
            for (var missing : listOfMaps(importPreview(snapshot, product).get("missingPaints"))) {
                requiredByPaint.computeIfAbsent(text(missing.get("id")), ignored -> new java.util.LinkedHashSet<>())
                        .add(product.id());
            }
        }
        var result = new ArrayList<Map<String, Object>>();
        var checkedById = new LinkedHashMap<String, Boolean>();
        snapshot.events().stream().filter(event -> "shopping_item.status_changed".equals(event.eventType()))
                .sorted(Comparator.comparing(DomainEvent::recordedAt).thenComparing(DomainEvent::eventId))
                .forEach(event -> checkedById.put(event.aggregateId(), Boolean.TRUE.equals(event.payload().get("checked"))));
        var plannedPaintIds = new java.util.HashSet<String>();
        for (var entry : snapshot.shopping()) {
            var paintId = text(entry.get("market_paint_id"));
            var paint = paintsById.get(paintId);
            var sourceProducts = requiredByPaint.getOrDefault(paintId, new java.util.LinkedHashSet<>());
            var view = new LinkedHashMap<String, Object>();
            view.put("id", text(entry.get("id")));
            view.put("kind", sourceProducts.isEmpty() ? "planned" : "required");
            view.put("planned", true);
            view.put("marketPaintId", paintId);
            view.put("brand", paint == null ? text(entry.get("brand")) : text(paint.get("brand")));
            view.put("name", paint == null ? text(entry.get("name")) : text(paint.get("name")));
            view.put("reference", paint == null ? text(entry.get("reference")) : text(paint.get("reference")));
            view.put("colorHex", paint == null ? text(entry.get("color_hex")) : text(map(paint.get("color")).get("hex")));
            view.put("reason", text(entry.get("reason")));
            view.put("priority", defaultText(text(entry.get("priority")), "low"));
            view.put("sourceProductIds", List.copyOf(sourceProducts));
            view.put("sourceProductNames", sourceProducts.stream().map(id -> productsById.getOrDefault(id, id)).toList());
            view.put("checked", checkedById.getOrDefault(text(entry.get("id")), false));
            result.add(Map.copyOf(view));
            if (present(paintId)) plannedPaintIds.add(paintId);
        }
        requiredByPaint.forEach((paintId, sourceProducts) -> {
            if (plannedPaintIds.contains(paintId)) return;
            var paint = paintsById.getOrDefault(paintId, Map.of());
            var view = new LinkedHashMap<String, Object>();
            view.put("id", "required-" + paintId);
            view.put("kind", "required");
            view.put("planned", false);
            view.put("marketPaintId", paintId);
            view.put("brand", text(paint.get("brand")));
            view.put("name", text(paint.get("name")));
            view.put("reference", text(paint.get("reference")));
            view.put("colorHex", text(map(paint.get("color")).get("hex")));
            view.put("reason", "");
            view.put("priority", "high");
            view.put("sourceProductIds", List.copyOf(sourceProducts));
            view.put("sourceProductNames", sourceProducts.stream().map(id -> productsById.getOrDefault(id, id)).toList());
            view.put("checked", checkedById.getOrDefault("required-" + paintId, false));
            result.add(Map.copyOf(view));
        });
        return List.copyOf(result);
    }

    private Map<String, Object> sourceView(Map<String, Object> source) {
        return Map.of("kind", text(source.get("kind")), "label", text(source.get("label")), "url", text(source.get("url")));
    }

    private Map<String, Object> sourceView(PaintableProduct.Source source) {
        return Map.of("kind", source.kind(), "label", source.label(), "url", source.url());
    }

    private Map<String, Object> imageView(Map<String, Object> image) {
        var view = new LinkedHashMap<String, Object>();
        view.put("url", text(image.get("url")));
        view.put("pageUrl", text(image.get("page_url")));
        view.put("credit", text(image.get("credit")));
        if (present(text(image.get("license")))) view.put("license", text(image.get("license")));
        return view;
    }

    private Map<String, Object> imageView(PaintableProduct.ReferenceImage image) {
        var view = new LinkedHashMap<String, Object>();
        view.put("url", image.url());
        view.put("pageUrl", image.pageUrl());
        view.put("credit", image.credit());
        if (present(image.license())) view.put("license", image.license());
        return view;
    }

    private Map<String, Object> stepView(Map<String, Object> step) {
        return Map.of("title", text(step.get("title")), "detail", text(step.get("detail")));
    }

    private Map<String, Object> workshopRecipeView(WorkshopRecipeState recipe) {
        var view = new LinkedHashMap<String, Object>();
        view.put("id", recipe.id());
        view.put("catalogItemId", recipe.catalogItemId());
        view.put("basedOnGuideId", defaultText(recipe.basedOnGuideId(), ""));
        view.put("supersedesRecipeId", defaultText(recipe.supersedesRecipeId(), ""));
        view.put("displayName", recipe.displayName());
        view.put("version", recipe.version());
        view.put("status", recipe.status().id());
        view.put("solutions", camelize(recipe.solutions()));
        view.put("updatedAt", recipe.updatedAt());
        return view;
    }

    private static PaintableProduct findProduct(DataSnapshot snapshot, String productId) {
        require(productId, "productId");
        return snapshot.paintableProducts().stream().filter(product -> productId.equals(product.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Paintable product not found: " + productId));
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
        snapshot.marketPaintingGuides().forEach(guide -> listOfMaps(guide.get("slots")).forEach(slot -> {
            var paintId = text(slot.get("market_paint_id"));
            if (present(paintId)) result.add(paintId);
        }));
        snapshot.events().stream().filter(event -> "workshop_recipe.created".equals(event.eventType()))
                .forEach(event -> collectPaintIds(event.payload().get("solutions"), result));
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

    private static void validateRecipeSolutions(
            List<Map<String, Object>> solutions, Map<String, Object> guide, DataSnapshot snapshot) {
        var ownedPaintIds = snapshot.paintInventory().stream()
                .filter(entry -> number(entry.get("quantity")) > 0)
                .map(entry -> text(entry.get("paint_id"))).collect(Collectors.toSet());
        var guideSlotIds = listOfMaps(guide.get("slots")).stream()
                .map(slot -> text(slot.get("id"))).collect(Collectors.toSet());
        var usedSlots = new java.util.HashSet<String>();
        for (var solution : solutions) {
            var type = text(solution.get("type"));
            if (!RECIPE_SOLUTION_TYPES.contains(type)) {
                throw new DomainException("invalid_input", "Unknown recipe solution type: " + type);
            }
            var slotId = text(solution.get("guide_slot_id"));
            if (!guide.isEmpty()) {
                require(slotId, "solutions.guide_slot_id");
                if (!guideSlotIds.contains(slotId)) throw new DomainException("invalid_input", "Unknown market guide slot: " + slotId);
                if (!usedSlots.add(slotId)) throw new DomainException("invalid_input", "A market guide slot can only have one workshop solution: " + slotId);
            }
            var paintIds = new java.util.LinkedHashSet<String>();
            if ("single_paint".equals(type)) {
                require(text(solution.get("paint_id")), "solutions.paint_id");
                paintIds.add(text(solution.get("paint_id")));
            } else {
                listOfMaps(solution.get("components")).forEach(component -> {
                    var paintId = text(component.get("paint_id"));
                    if (present(paintId)) paintIds.add(paintId);
                });
                if (("mixture".equals(type) || "layer_stack".equals(type)) && paintIds.isEmpty()) {
                    throw new DomainException("invalid_input", type + " requires paint components.");
                }
                if ("technique".equals(type) && !present(text(solution.get("instructions")))) {
                    throw new DomainException("invalid_input", "A technique solution requires instructions.");
                }
            }
            var missing = paintIds.stream().filter(id -> !ownedPaintIds.contains(id)).toList();
            if (!missing.isEmpty()) {
                throw new DomainException("conflict", "Workshop recipe can only use owned paints: " + String.join(", ", missing));
            }
        }
    }

    private DomainEvent idempotent(DataSnapshot snapshot, String key) {
        if (!present(key)) return null;
        return snapshot.events().stream().filter(event -> key.equals(event.idempotencyKey())).findFirst().orElse(null);
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

    private static String csv(Object value) {
        var text = text(value);
        return text.matches(".*[,\"\\r\\n].*") ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    private static String quoted(Object value) {
        return "\"" + text(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
