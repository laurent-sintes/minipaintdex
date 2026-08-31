package com.minipaintdex.application;

import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.AddWorkshopItemCommentCommand;
import com.minipaintdex.application.command.AddWorkshopItemPhotoCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.command.TransitionStageCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.command.TransitionPaintingProjectCommand;
import com.minipaintdex.application.command.SetShoppingItemStatusCommand;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventLedger;
import com.minipaintdex.application.port.EventBus;
import com.minipaintdex.application.event.EventBatch;
import com.minipaintdex.application.event.EventPublicationStatus;
import com.minipaintdex.application.event.PublicationReceipt;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.WorkshopMediaStorage;
import com.minipaintdex.application.result.CreatePaintingProjectResult;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.MissingPaintView;
import com.minipaintdex.application.view.PaintableProductSummaryView;
import com.minipaintdex.application.view.PaintableProductView;
import com.minipaintdex.application.view.PaintingProjectView;
import com.minipaintdex.application.view.ProductImportPreviewView;
import com.minipaintdex.application.view.ShoppingItemView;
import com.minipaintdex.application.view.WorkshopItemView;
import com.minipaintdex.application.view.WorkshopOverviewView;
import com.minipaintdex.application.view.WorkshopRecipeView;
import com.minipaintdex.application.validation.MarketCatalogFactory;
import com.minipaintdex.application.validation.StructuredDocuments;
import com.minipaintdex.application.view.GuideReconciliationView;
import com.minipaintdex.application.view.MarketPaintingGuideView;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.AggregateRoot;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.market.paint.PaintMatchEngine;
import com.minipaintdex.domain.market.paint.MarketPaint;
import com.minipaintdex.domain.market.guide.MarketPaintingGuide;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application composition retained while use cases are exposed through segregated input ports.
 * Mutating methods serialize the local load-decide-durable-accept window so a later command sees
 * every earlier accepted event; the ledger additionally enforces aggregate versions under its file lock.
 */
public final class WorkshopCommandService {
    private final SnapshotRepository snapshots;
    private final EventBus eventBus;
    private final WorkshopMediaStorage mediaStorage;
    private final WorkshopQueryService queries;
    private final WorkshopMediaPolicy mediaPolicy;
    private final DomainEventEnvelopeFactory envelopeFactory = new DomainEventEnvelopeFactory();

    public WorkshopCommandService(
            SnapshotRepository snapshots,
            EventBus eventBus,
            WorkshopMediaStorage mediaStorage,
            WorkshopMediaPolicy mediaPolicy,
            WorkshopQueryService queries) {
        this.snapshots = Objects.requireNonNull(snapshots);
        this.eventBus = Objects.requireNonNull(eventBus);
        this.mediaStorage = Objects.requireNonNull(mediaStorage);
        this.mediaPolicy = Objects.requireNonNull(mediaPolicy);
        this.queries = Objects.requireNonNull(queries);
    }

    public synchronized CreatePaintingProjectResult createPaintingProject(CreatePaintingProjectCommand command) {
        require(command.paintableProductId(), "paintableProductId");
        var snapshot = snapshots.load();
        var product = queries.findProduct(snapshot, command.paintableProductId());
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
        if (workshop == null) {
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
        MarketPaintingGuide guide = null;
        if (present(command.basedOnGuideId())) {
            guide = queries.marketCatalog(snapshot).paintingGuides().stream()
                    .filter(candidate -> command.basedOnGuideId().equals(candidate.id()))
                    .findFirst().orElseThrow(() -> new DomainException("not_found", "Market painting guide not found: " + command.basedOnGuideId()));
            if (!command.catalogItemId().equals(guide.catalogItemId())) {
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

    public synchronized PublicationReceipt addWorkshopItem(AddWorkshopItemCommand command) {
        require(command.catalogItemId(), "catalogItemId");
        require(command.paintingProjectId(), "paintingProjectId");
        require(command.displayName(), "displayName");
        var snapshot = snapshots.load();
        var paintingProject = paintingProject(snapshot, command.paintingProjectId());
        var product = queries.findProduct(snapshot, paintingProject.paintableProductId());
        if (WorkshopProjector.project(snapshot.events())
                .filter(workshop -> workshop.containsPaintingProject(paintingProject.id())).isEmpty()) {
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
        var stage = present(command.stage()) ? WorkflowStage.fromId(command.stage()) : null;
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var mediaId = "media-" + Ulid.next(occurredAt).toLowerCase(Locale.ROOT);
        var stored = mediaStorage.store(item.id(), mediaId, command.originalFilename(), contentType, content);
        var workshopItem = workshopItemAggregate(snapshot, item.id());
        workshopItem.addPhoto(
                stored.id(), stored.publicPath(), stage, command.caption(), stored.originalFilename(),
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
        var known = queries.shoppingViews(snapshot).stream().anyMatch(item -> command.itemId().equals(item.id()));
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
                    .map(WorkshopCommandService::existingReceipt)
                    .orElseThrow(() -> new DomainException("no_change", "Shopping item status is already current."));
        }
        return publish(shoppingItem,
                new Actor("user", defaultText(command.actorId(), "owner")),
                command.correlationId(), command.idempotencyKey());
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
        return history.isEmpty() ? null : Workshop.rehydrate(history);
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
            List<RecipeSolution> solutions, MarketPaintingGuide guide, DataSnapshot snapshot) {
        var ownedPaintIds = StructuredDocuments.toMaps(snapshot.paintInventory()).stream()
                .filter(entry -> StructuredDocuments.integer(
                        entry.get("quantity"), "paint_inventory.quantity") > 0)
                .map(entry -> StructuredDocuments.text(entry.get("paint_id"))).collect(Collectors.toSet());
        var guideSlotIds = guide == null ? Set.<String>of() : guide.slots().stream()
                .map(MarketPaintingGuide.Slot::id).collect(Collectors.toSet());
        var usedSlots = new java.util.HashSet<String>();
        for (var solution : solutions) {
            var slotId = defaultText(solution.guideSlotId(), "");
            if (guide != null) {
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

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String defaultText(String value, String fallback) {
        return present(value) ? value : fallback;
    }

    private static void require(String value, String field) {
        if (!present(value)) throw new DomainException("invalid_input", field + " is required.");
    }

}
