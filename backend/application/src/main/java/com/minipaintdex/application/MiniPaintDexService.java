package com.minipaintdex.application;

import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.ApplyMarketPaintChangeSetCommand;
import com.minipaintdex.application.command.ApplyMiniatureProjectChangeSetCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.TransitionStageCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventLedger;
import com.minipaintdex.application.port.MarketPaintCatalogWriter;
import com.minipaintdex.application.port.ProjectCatalogWriter;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.WorkshopPaintInventoryWriter;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.result.ApplyMarketPaintChangeSetResult;
import com.minipaintdex.application.result.ApplyMiniatureProjectChangeSetResult;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.paint.PaintMatchEngine;
import com.minipaintdex.domain.workflow.DomainException;
import com.minipaintdex.domain.workflow.StageAction;
import com.minipaintdex.domain.workflow.WorkflowStage;
import com.minipaintdex.domain.workflow.WorkflowStageStatus;
import com.minipaintdex.domain.workshop.WorkshopItemProjector;
import com.minipaintdex.domain.workshop.WorkshopItemState;
import com.minipaintdex.domain.workshop.WorkshopRecipeProjector;
import com.minipaintdex.domain.workshop.WorkshopRecipeState;
import com.minipaintdex.domain.workshop.WorkshopRecipeStatus;

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
    private final PaintMatchEngine paintMatchEngine = new PaintMatchEngine();
    private final SnapshotRepository snapshots;
    private final EventLedger ledger;
    private final MarketPaintCatalogWriter marketPaints;
    private final WorkshopPaintInventoryWriter workshopPaints;
    private final ProjectCatalogWriter projects;

    public MiniPaintDexService(
            SnapshotRepository snapshots,
            EventLedger ledger,
            MarketPaintCatalogWriter marketPaints,
            WorkshopPaintInventoryWriter workshopPaints,
            ProjectCatalogWriter projects) {
        this.snapshots = Objects.requireNonNull(snapshots);
        this.ledger = Objects.requireNonNull(ledger);
        this.marketPaints = Objects.requireNonNull(marketPaints);
        this.workshopPaints = Objects.requireNonNull(workshopPaints);
        this.projects = Objects.requireNonNull(projects);
    }

    public Map<String, Object> bootstrap() {
        return project(snapshots.load());
    }

    public List<Map<String, Object>> searchMarketPaints(SearchMarketPaintsQuery filters) {
        @SuppressWarnings("unchecked")
        var paints = (List<Map<String, Object>>) bootstrap().get("paints");
        var query = text(filters.query()).toLowerCase(Locale.ROOT);
        return paints.stream().filter(paint -> {
            if (!matches(filters.brand(), paint.get("brand"))) return false;
            if (!matches(filters.range(), paint.get("range"))) return false;
            if (!matches(filters.type(), paint.get("paintType"))) return false;
            if (!matches(filters.color(), paint.get("colorFamily"))) return false;
            if (!matches(filters.finish(), paint.get("finish"))) return false;
            if (!matches(filters.medium(), paint.get("medium"))) return false;
            if (!matches(filters.opacity(), paint.get("opacity"))) return false;
            if (!matches(filters.volume(), paint.get("volumeMl"))) return false;
            if (!matches(filters.reference(), paint.get("reference"))) return false;
            if (!matches(filters.lifecycle(), paint.get("lifecycleStatus"))) return false;
            if (!matches(filters.manufacturer(), paint.get("manufacturer"))) return false;
            if (present(filters.tag()) && strings(paint.get("tags")).stream().noneMatch(tag -> tag.equalsIgnoreCase(filters.tag()))) return false;
            if (!query.isBlank()) {
                var haystack = String.join(" ", text(paint.get("name")), text(paint.get("brand")), text(paint.get("manufacturer")), text(paint.get("range")), text(paint.get("reference")), text(paint.get("tags"))).toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) return false;
            }
            return true;
        }).toList();
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
            marketPaints.replaceMarketPaints(result);
            if (inventoryChanged > 0) workshopPaints.replaceWorkshopPaints(inventory);
        }
        return new ApplyMarketPaintChangeSetResult(
                added, updated, retired, deleted, unchanged, inventoryChanged, result.size(), !command.dryRun());
    }

    public ApplyMiniatureProjectChangeSetResult applyMiniatureProjectChangeSet(ApplyMiniatureProjectChangeSetCommand command) {
        if (command.schemaVersion() != 1) throw new DomainException("invalid_input", "schemaVersion must be 1.");
        if (!"miniature_project".equals(command.kind())) throw new DomainException("invalid_input", "kind must be miniature_project.");
        var projectId = text(command.project().get("id"));
        require(projectId, "project.id");
        if (!projectId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new DomainException("invalid_input", "Project id must use lowercase kebab-case.");
        }
        for (var field : List.of("name", "game", "scope")) require(text(command.project().get(field)), "project." + field);
        var catalogItems = listOfMaps(command.project().get("catalog_items"));
        if (catalogItems.isEmpty()) throw new DomainException("invalid_input", "project.catalog_items is required.");
        var catalogItemIds = catalogItems.stream().map(item -> text(item.get("id"))).collect(Collectors.toSet());
        if (catalogItemIds.size() != catalogItems.size() || catalogItemIds.stream().anyMatch(id -> !present(id))) {
            throw new DomainException("invalid_input", "Catalog item ids must be present and unique.");
        }
        if (catalogItems.stream().anyMatch(item -> !projectId.equals(text(item.get("game_id"))))) {
            throw new DomainException("invalid_input", "Every catalog item must reference project.id in game_id.");
        }
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
        var itemIds = command.workshopItems().stream().map(ApplyMiniatureProjectChangeSetCommand.WorkshopItem::id).collect(Collectors.toSet());
        if (itemIds.size() != command.workshopItems().size() || itemIds.stream().anyMatch(id -> !present(id))) {
            throw new DomainException("invalid_input", "Workshop item ids must be present and unique.");
        }
        for (var item : command.workshopItems()) {
            if (!projectId.equals(item.projectId())) throw new DomainException("invalid_input", "Workshop item projectId must reference project.id.");
            if (!catalogItemIds.contains(item.catalogItemId())) throw new DomainException("invalid_input", "Workshop item references an unknown catalog item: " + item.catalogItemId());
            require(item.displayName(), "workshopItems.displayName");
        }
        var expected = number(command.project().get("expected_paintable_count"));
        if (expected > 0 && expected != command.workshopItems().size()) {
            throw new DomainException("invalid_input", "Workshop item count does not match expected_paintable_count.");
        }
        var existing = WorkshopItemProjector.project(snapshots.load().events()).stream()
                .map(WorkshopItemState::id)
                .collect(Collectors.toSet());
        var existingCount = (int) command.workshopItems().stream().filter(item -> existing.contains(item.id())).count();
        var addedCount = command.workshopItems().size() - existingCount;
        if (!command.dryRun()) {
            projects.replaceProject(projectId, command.project(), command.paintingGuides());
            var correlationId = defaultText(command.correlationId(), Ulid.next(Instant.now()));
            for (var item : command.workshopItems()) {
                if (existing.contains(item.id())) continue;
                addWorkshopItem(new AddWorkshopItemCommand(
                        item.id(), item.catalogItemId(), item.projectId(), item.displayName(),
                        defaultText(command.actorId(), "owner"), null, correlationId,
                        "project-import:" + projectId + ":" + item.id()));
            }
        }
        return new ApplyMiniatureProjectChangeSetResult(
                projectId, catalogItems.size(), command.paintingGuides().size(), addedCount, existingCount, !command.dryRun());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listProjects() {
        return (List<Map<String, Object>>) bootstrap().get("projects");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listWorkshopItems(String projectId) {
        var items = (List<Map<String, Object>>) bootstrap().get("workshopItems");
        if (!present(projectId)) return items;
        return items.stream().filter(item -> projectId.equals(item.get("projectId"))).toList();
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
                    .rank(paintProfile(sourcePaint), ownedProfiles, 5).stream()
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
            result.put("requiresManualReview", sourcePaint != null && isBehavioralPaint(sourcePaint));
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
        var projectId = projectIdForCatalogItem(snapshot, command.catalogItemId());
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
        if (present(command.basedOnGuideId())) payload.put("based_on_guide_id", command.basedOnGuideId());
        if (present(command.supersedesRecipeId())) payload.put("supersedes_recipe_id", command.supersedesRecipeId());
        var event = new DomainEvent(Ulid.next(occurredAt), 1, "workshop_recipe.created", occurredAt, Instant.now(),
                "workshop_recipe", recipeId, projectId, new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(), payload);
        ledger.append(event);
        return event;
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
        var payload = present(command.comment()) ? Map.<String, Object>of("comment", command.comment()) : Map.<String, Object>of();
        var event = new DomainEvent(Ulid.next(occurredAt), 1, WorkshopRecipeProjector.eventType(command.action()),
                occurredAt, Instant.now(), "workshop_recipe", recipe.id(), projectIdForCatalogItem(snapshot, recipe.catalogItemId()),
                new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(), payload);
        ledger.append(event);
        return event;
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
                "workshop_item", item.id(), item.projectId(), new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(),
                Map.of("recipe_id", recipe.id(), "recipe_version", recipe.version()));
        ledger.append(event);
        return event;
    }

    public List<DomainEvent> listActivity(String projectId) {
        return snapshots.load().events().stream()
                .filter(event -> !present(projectId) || projectId.equals(event.projectId()))
                .sorted(Comparator.comparing(DomainEvent::recordedAt).reversed())
                .toList();
    }

    public DomainEvent addWorkshopItem(AddWorkshopItemCommand command) {
        require(command.catalogItemId(), "catalogItemId");
        require(command.projectId(), "projectId");
        require(command.displayName(), "displayName");
        var snapshot = snapshots.load();
        var game = snapshot.games().stream().filter(candidate -> command.projectId().equals(candidate.get("id"))).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Project not found: " + command.projectId()));
        var catalogItemExists = listOfMaps(game.get("catalog_items")).stream()
                .anyMatch(item -> command.catalogItemId().equals(item.get("id")));
        if (!catalogItemExists) throw new DomainException("not_found", "Catalog item not found in project: " + command.catalogItemId());
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return duplicate;
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var itemId = present(command.itemId()) ? command.itemId() : "ws-" + command.catalogItemId() + "-" + Ulid.next(occurredAt).toLowerCase(Locale.ROOT);
        if (snapshot.events().stream().anyMatch(event -> itemId.equals(event.aggregateId()) && "workshop_item.added".equals(event.eventType()))) {
            throw new DomainException("conflict", "Workshop item already exists: " + itemId);
        }
        var event = new DomainEvent(Ulid.next(occurredAt), 1, "workshop_item.added", occurredAt, Instant.now(),
                "workshop_item", itemId, command.projectId(), new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(),
                Map.of("catalog_item_id", command.catalogItemId(), "display_name", command.displayName()));
        ledger.append(event);
        return event;
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
        WorkshopItemProjector.assertTransition(item.workflow().get(stage), action);
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("stage", stage.id());
        if (present(command.comment())) payload.put("comment", command.comment());
        if (present(command.reason())) payload.put("reason", command.reason());
        var event = new DomainEvent(Ulid.next(occurredAt), 1, action.eventType(), occurredAt, Instant.now(),
                "workshop_item", item.id(), item.projectId(), new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(), payload);
        ledger.append(event);
        return event;
    }

    public Map<String, Object> rebuildProjections() {
        var view = bootstrap();
        return Map.of(
                "paints", size(view.get("paints")),
                "projects", size(view.get("projects")),
                "workshopItems", size(view.get("workshopItems")),
                "marketPaintingGuides", size(view.get("marketPaintingGuides")),
                "workshopRecipes", size(view.get("workshopRecipes")));
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
        var result = new LinkedHashMap<String, Object>();
        result.put("paints", paints);
        result.put("projects", projectViews(snapshot, paints, items));
        result.put("workshopItems", items.stream().map(this::workshopItemView).toList());
        result.put("marketPaintingGuides", snapshot.marketPaintingGuides().stream().map(MiniPaintDexService::camelize).toList());
        result.put("workshopRecipes", workshopRecipes.stream().map(this::workshopRecipeView).toList());
        result.put("shoppingSeed", shoppingViews(snapshot));
        result.put("config", camelize(snapshot.site()));
        return result;
    }

    private List<Map<String, Object>> paintViews(DataSnapshot snapshot) {
        var quantities = snapshot.paintInventory().stream().collect(Collectors.toMap(entry -> text(entry.get("paint_id")), entry -> number(entry.get("quantity")), Integer::sum));
        return snapshot.marketPaints().stream().map(entry -> {
            var color = map(entry.get("color"));
            var manufacturerImage = map(entry.get("manufacturer_image"));
            var resultImage = map(entry.get("result_image"));
            var verifiedAt = text(entry.get("verified_at"));
            Map<String, Object> paint = new LinkedHashMap<>();
            paint.put("id", text(entry.get("id")));
            paint.put("brand", text(entry.get("brand")));
            paint.put("manufacturer", text(entry.get("manufacturer")));
            paint.put("brandAliases", strings(entry.get("brand_aliases")));
            paint.put("range", text(entry.get("range")));
            paint.put("paintType", text(entry.get("functional_type")));
            paint.put("reference", text(entry.get("reference")));
            paint.put("name", text(entry.get("name")));
            paint.put("colorHex", defaultText(text(color.get("hex")), "#777777"));
            paint.put("finish", text(entry.get("finish")));
            paint.put("medium", text(entry.get("medium")));
            paint.put("opacity", text(entry.get("opacity")));
            paint.put("lifecycleStatus", text(entry.get("lifecycle_status")));
            paint.put("quantity", quantities.getOrDefault(text(entry.get("id")), 0));
            paint.put("status", text(entry.get("data_status")));
            paint.put("warnings", String.join(" · ", strings(entry.get("warnings"))));
            paint.put("tags", strings(entry.get("tags")));
            paint.put("notes", text(entry.get("notes")));
            paint.put("createdAt", verifiedAt.isBlank() ? "" : verifiedAt + "T00:00:00.000Z");
            paint.put("updatedAt", verifiedAt.isBlank() ? "" : verifiedAt + "T00:00:00.000Z");
            paint.put("manufacturerUrl", text(entry.get("manufacturer_page")));
            paint.put("manufacturerImage", text(manufacturerImage.get("path")));
            paint.put("manufacturerImageCredit", text(manufacturerImage.get("credit")));
            paint.put("volumeMl", number(entry.get("volume_ml")));
            paint.put("colorFamily", text(color.get("family")));
            paint.put("manufacturerDescription", text(entry.get("notes")));
            paint.put("recommendedUses", strings(entry.get("recommended_uses")));
            var usageInstructions = map(entry.get("usage_instructions"));
            paint.put("usageInstructions", Map.of(
                    "summary", text(usageInstructions.get("summary")),
                    "steps", strings(usageInstructions.get("steps")),
                    "tips", strings(usageInstructions.get("tips"))));
            paint.put("manufacturerVerifiedAt", verifiedAt);
            paint.put("resultImage", text(resultImage.get("path")));
            paint.put("resultImageCredit", text(resultImage.get("credit")));
            paint.put("resultImageSource", text(resultImage.get("source_url")));
            paint.put("resultImageLicense", text(resultImage.get("license")));
            paint.put("resultReferenceUrl", text(resultImage.get("reference_url")));
            return paint;
        }).sorted(Comparator.comparing(entry -> text(entry.get("name")), String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private List<Map<String, Object>> projectViews(DataSnapshot snapshot, List<Map<String, Object>> paints, List<WorkshopItemState> workshopItems) {
        var guides = snapshot.marketPaintingGuides().stream().collect(Collectors.toMap(
                entry -> text(entry.get("catalog_item_id")), Function.identity(),
                (left, right) -> number(left.get("version")) >= number(right.get("version")) ? left : right));
        var workshopRecipes = WorkshopRecipeProjector.project(snapshot.events());
        var paintsById = paints.stream().collect(Collectors.toMap(entry -> text(entry.get("id")), Function.identity()));
        return snapshot.games().stream().map(game -> {
            var gameId = text(game.get("id"));
            var projectItems = listOfMaps(game.get("catalog_items")).stream().map(item -> {
                var catalogItemId = text(item.get("id"));
                var guide = guides.getOrDefault(catalogItemId, Map.of());
                var physicalItems = workshopItems.stream().filter(candidate -> catalogItemId.equals(candidate.catalogItemId())).toList();
                var allCompleted = !physicalItems.isEmpty() && physicalItems.stream().allMatch(WorkshopItemState::completed);
                var anyStarted = physicalItems.stream().anyMatch(candidate -> candidate.workflow().values().stream().anyMatch(status -> status != WorkflowStageStatus.PENDING));
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("id", catalogItemId.startsWith(gameId + "-") ? catalogItemId.substring(gameId.length() + 1) : catalogItemId);
                view.put("name", text(item.get("name")));
                view.put("kind", text(item.get("kind")));
                view.put("quantity", physicalItems.size());
                view.put("status", allCompleted ? "completed" : anyStarted ? "in_progress" : "pending");
                view.put("description", text(item.get("description")));
                view.put("referenceImages", listOfMaps(item.get("reference_images")).stream().map(this::imageView).toList());
                view.put("paints", listOfMaps(guide.get("slots")).stream().map(slot -> guideSlotView(slot, paintsById)).toList());
                view.put("preparation", listOfMaps(guide.get("preparation")).stream().map(this::stepView).toList());
                view.put("painting", listOfMaps(guide.get("painting")).stream().map(this::stepView).toList());
                view.put("marketGuide", guide.isEmpty() ? Map.of() : Map.of(
                        "id", text(guide.get("id")), "version", number(guide.get("version")),
                        "knowledgeStatus", text(guide.get("knowledge_status")),
                        "sources", listOfMaps(guide.get("sources")).stream().map(this::sourceView).toList()));
                view.put("workshopRecipes", workshopRecipes.stream()
                        .filter(recipe -> catalogItemId.equals(recipe.catalogItemId()))
                        .map(this::workshopRecipeView).toList());
                view.put("sources", listOfMaps(item.get("sources")).stream().map(this::sourceView).toList());
                return view;
            }).toList();
            var edition = map(game.get("edition"));
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("schemaVersion", number(game.getOrDefault("schema_version", 1)));
            view.put("id", gameId);
            view.put("name", text(game.get("name")));
            view.put("game", text(game.get("game")));
            view.put("scope", text(game.get("scope")));
            view.put("edition", Map.of("note", text(edition.get("note")), "url", text(edition.get("url"))));
            view.put("sources", listOfMaps(game.get("sources")).stream().map(this::sourceView).toList());
            view.put("items", projectItems);
            return view;
        }).sorted(Comparator.comparing(entry -> text(entry.get("name")), String.CASE_INSENSITIVE_ORDER)).toList();
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
        view.put("colorHex", paint == null ? defaultText(text(requested.get("color_hex")), "#777777") : paint.get("colorHex"));
        if (Boolean.TRUE.equals(slot.get("pending_import"))) view.put("pendingImport", true);
        return view;
    }

    private Map<String, Object> workshopItemView(WorkshopItemState item) {
        var workflow = new LinkedHashMap<String, Object>();
        for (var stage : WorkflowStage.values()) workflow.put(stage.id(), item.workflow().get(stage).id());
        var view = new LinkedHashMap<String, Object>();
        view.put("id", item.id());
        view.put("catalogItemId", item.catalogItemId());
        view.put("projectId", item.projectId());
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
        return snapshot.shopping().stream().map(entry -> Map.<String, Object>of(
                "id", text(entry.get("id")), "brand", text(entry.get("brand")), "name", text(entry.get("name")),
                "reference", text(entry.get("reference")), "colorHex", defaultText(text(entry.get("color_hex")), "#777777"),
                "reason", text(entry.get("reason")), "priority", defaultText(text(entry.get("priority")), "low"))).toList();
    }

    private Map<String, Object> sourceView(Map<String, Object> source) {
        return Map.of("kind", text(source.get("kind")), "label", text(source.get("label")), "url", text(source.get("url")));
    }

    private Map<String, Object> imageView(Map<String, Object> image) {
        var view = new LinkedHashMap<String, Object>();
        view.put("url", text(image.get("url")));
        view.put("pageUrl", text(image.get("page_url")));
        view.put("credit", text(image.get("credit")));
        if (present(text(image.get("license")))) view.put("license", text(image.get("license")));
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

    private PaintMatchEngine.Paint paintProfile(Map<String, Object> paint) {
        var behaviorTags = new java.util.LinkedHashSet<String>();
        behaviorTags.addAll(strings(paint.get("behavior_tags")));
        behaviorTags.addAll(strings(paint.get("tags")));
        map(paint.get("application_profile")).values().forEach(value -> {
            if (value instanceof List<?> values) values.forEach(entry -> behaviorTags.add(text(entry)));
            else if (present(text(value))) behaviorTags.add(text(value));
        });
        return new PaintMatchEngine.Paint(
                text(paint.get("id")), defaultText(text(map(paint.get("color")).get("hex")), "#777777"),
                text(paint.get("functional_type")), text(paint.get("finish")), text(paint.get("opacity")),
                text(paint.get("medium")), Set.copyOf(behaviorTags));
    }

    private static boolean isBehavioralPaint(Map<String, Object> paint) {
        return Set.of("one_coat_contrast", "technical_effect", "primer", "wash_shade", "ink", "auxiliary")
                .contains(text(paint.get("functional_type")));
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

    private static String projectIdForCatalogItem(DataSnapshot snapshot, String catalogItemId) {
        for (var game : snapshot.games()) {
            if (listOfMaps(game.get("catalog_items")).stream().anyMatch(item -> catalogItemId.equals(text(item.get("id"))))) {
                return text(game.get("id"));
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

    private static boolean matches(String expected, Object actual) {
        return !present(expected) || expected.equalsIgnoreCase(text(actual));
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
