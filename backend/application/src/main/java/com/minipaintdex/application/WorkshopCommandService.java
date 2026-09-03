package com.minipaintdex.application;

import com.minipaintdex.application.command.AddWorkshopPaintableCommand;
import com.minipaintdex.application.command.AddWorkshopPaintableCommentCommand;
import com.minipaintdex.application.command.AddWorkshopPaintablePhotoCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.command.TransitionWorkshopPaintableStageCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.command.TransitionPaintingProjectCommand;
import com.minipaintdex.application.command.SetShoppingListEntryCheckedCommand;
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
import com.minipaintdex.application.view.PaintProductView;
import com.minipaintdex.application.view.MissingPaintView;
import com.minipaintdex.application.view.PaintableProductSummaryView;
import com.minipaintdex.application.view.PaintableProductView;
import com.minipaintdex.application.view.PaintingProjectView;
import com.minipaintdex.application.view.PaintingProjectImportPreviewView;
import com.minipaintdex.application.view.ShoppingListEntryView;
import com.minipaintdex.application.view.WorkshopPaintableView;
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
import com.minipaintdex.domain.market.paint.PaintProduct;
import com.minipaintdex.domain.market.guide.MarketPaintingGuide;
import com.minipaintdex.domain.market.product.PaintableProduct;
import com.minipaintdex.domain.shared.DomainException;
import com.minipaintdex.domain.workshop.StageAction;
import com.minipaintdex.domain.workshop.WorkflowStage;
import com.minipaintdex.domain.workshop.WorkflowStageStatus;
import com.minipaintdex.domain.workshop.WorkshopPaintableProjector;
import com.minipaintdex.domain.workshop.WorkshopPaintableState;
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
import com.minipaintdex.domain.workshop.ShoppingListEntry;
import com.minipaintdex.domain.workshop.ShoppingListEntryEvent;
import com.minipaintdex.domain.workshop.ShoppingListEntryCheckedChanged;
import com.minipaintdex.domain.workshop.WorkshopEvent;
import com.minipaintdex.domain.workshop.WorkshopPaintable;
import com.minipaintdex.domain.workshop.WorkshopPaintableEvent;
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


    public synchronized com.minipaintdex.application.result.ImportPaintPotsResult importPaintPots(
            com.minipaintdex.application.command.ImportPaintPotsCommand command) {
        if (command.schemaVersion() != 1 || !"workshop_paint_pots".equals(command.kind())) {
            throw new DomainException("invalid_input", "Expected workshop_paint_pots schema version 1.");
        }
        var snapshot = snapshots.load();
        var products = queries.marketCatalog(snapshot).paints().stream().map(paint -> paint.id()).collect(Collectors.toSet());
        var pots = com.minipaintdex.domain.workshop.PaintPotProjector.project(snapshot.events()).stream()
                .collect(Collectors.toMap(com.minipaintdex.domain.workshop.PaintPot::id, Function.identity()));
        var at = Instant.now();
        var correlation = defaultText(command.correlationId(), Ulid.next(at));
        var actor = new Actor("user", defaultText(command.actorId(), "owner"));
        var ids = new java.util.HashSet<String>();
        var events = new ArrayList<EventEnvelope>();
        var added = 0;
        var existing = 0;
        for (var registration : command.pots()) {
            require(registration.paintPotId(), "paintPotId");
            require(registration.paintProductId(), "paintProductId");
            if (!ids.add(registration.paintPotId())) throw new DomainException("invalid_input", "Duplicate paint pot ID: " + registration.paintPotId());
            if (!products.contains(registration.paintProductId())) throw new DomainException("not_found", "Paint product not found: " + registration.paintProductId());
            var previous = pots.get(registration.paintPotId());
            if (previous != null) {
                if (!previous.paintProductId().equals(registration.paintProductId())
                        || (registration.acquiredAt() != null && !registration.acquiredAt().equals(previous.acquiredAt()))) {
                    throw new DomainException("conflict", "An existing paint pot cannot be replaced by an import: " + registration.paintPotId());
                }
                existing++;
            } else {
                var pot = com.minipaintdex.domain.workshop.PaintPot.register(
                        registration.paintPotId(), registration.paintProductId(), registration.acquiredAt(), at);
                events.addAll(envelopeFactory.envelop(pot, actor, correlation, null, "register-pot:" + pot.id(), at));
                added++;
            }
        }
        if (!events.isEmpty() && workshopAggregate(snapshot) == null) {
            var workshop = Workshop.create(Workshop.DEFAULT_ID, "My workshop", at);
            events.addAll(0, envelopeFactory.envelop(workshop, actor, correlation, null, "create-workshop", at));
        }
        var receipt = command.dryRun() || events.isEmpty() ? null : publish(events, correlation, command.idempotencyKey());
        return new com.minipaintdex.application.result.ImportPaintPotsResult(added, existing, receipt != null, receipt);
    }

    public synchronized com.minipaintdex.application.result.ImportPaintPotsResult registerPaintPot(
            com.minipaintdex.application.command.RegisterPaintPotCommand command) {
        return importPaintPots(new com.minipaintdex.application.command.ImportPaintPotsCommand(
                1, "workshop_paint_pots", List.of(new com.minipaintdex.application.command.ImportPaintPotsCommand.Registration(
                        command.paintPotId(), command.paintProductId(), command.acquiredAt())),
                false, command.actorId(), command.correlationId(), command.idempotencyKey()));
    }

    public synchronized PublicationReceipt observePaintPot(com.minipaintdex.application.command.ObservePaintPotCommand command) {
        return changePaintPot(command.paintPotId(), command.actorId(), command.occurredAt(), command.correlationId(), command.idempotencyKey(),
                (pot, at) -> pot.observe(com.minipaintdex.domain.workshop.PaintPotCondition.fromId(command.condition()),
                        com.minipaintdex.domain.workshop.PaintPotRemainingLevel.fromId(command.remainingLevel()), at));
    }
    public synchronized PublicationReceipt openPaintPot(com.minipaintdex.application.command.OpenPaintPotCommand command) {
        return changePaintPot(command.paintPotId(), command.actorId(), command.occurredAt(), command.correlationId(), command.idempotencyKey(),
                (pot, at) -> pot.open(at));
    }
    public synchronized PublicationReceipt changePaintPotPossession(com.minipaintdex.application.command.ChangePaintPotPossessionCommand command) {
        return changePaintPot(command.paintPotId(), command.actorId(), command.occurredAt(), command.correlationId(), command.idempotencyKey(),
                (pot, at) -> pot.changePossession(com.minipaintdex.domain.workshop.PaintPotPossession.fromId(command.possession()), at));
    }
    public synchronized PublicationReceipt addPaintPotNote(com.minipaintdex.application.command.AddPaintPotNoteCommand command) {
        return changePaintPot(command.paintPotId(), command.actorId(), command.occurredAt(), command.correlationId(), command.idempotencyKey(),
                (pot, at) -> pot.addNote(requiredText(command.note(), "note"), at));
    }
    private PublicationReceipt changePaintPot(String id, String actorId, Instant at, String correlation, String key,
            java.util.function.BiConsumer<com.minipaintdex.domain.workshop.PaintPot, Instant> action) {
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, key);
        if (duplicate != null) return existingReceipt(duplicate);
        var pot = paintPot(snapshot, id);
        action.accept(pot, at == null ? Instant.now() : at);
        return publish(pot, new Actor("user", defaultText(actorId, "owner")), correlation, key);
    }
    private static com.minipaintdex.domain.workshop.PaintPot paintPot(DataSnapshot snapshot, String id) {
        require(id, "paintPotId");
        return com.minipaintdex.domain.workshop.PaintPotProjector.project(snapshot.events()).stream()
                .filter(pot -> pot.id().equals(id)).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Paint pot not found: " + id));
    }
    public synchronized PublicationReceipt addPaintPotPhoto(com.minipaintdex.application.command.AddPaintPotPhotoCommand command) {
        require(command.originalFilename(), "originalFilename");
        var type = defaultText(command.contentType(), "").toLowerCase(Locale.ROOT);
        if (!mediaPolicy.allowedContentTypes().contains(type)) throw new DomainException("invalid_input", "Unsupported pot photo content type.");
        var content = command.content();
        if (content.length == 0 || content.length > mediaPolicy.maxUploadBytes()) throw new DomainException("invalid_input", "Pot photo exceeds upload limit.");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var pot = paintPot(snapshot, command.paintPotId());
        var at = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var stored = mediaStorage.store(pot.id(), "media-" + Ulid.next(at).toLowerCase(Locale.ROOT), command.originalFilename(), type, content);
        pot.addPhoto(stored.id(), stored.publicPath(), command.caption(), stored.originalFilename(), stored.contentType(), stored.size(), stored.sha256(), at);
        try {
            return publish(pot, new Actor("user", defaultText(command.actorId(), "owner")), command.correlationId(), command.idempotencyKey());
        } catch (RuntimeException failure) {
            mediaStorage.delete(stored);
            throw failure;
        }
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
        var existingItems = WorkshopPaintableProjector.project(snapshot.events());
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

        var existingIds = existingItems.stream().map(WorkshopPaintableState::id).collect(Collectors.toSet());
        var added = 0;
        for (var paintableComponent : product.paintableComponents()) {
            for (var ordinal = 1; ordinal <= paintableComponent.quantity(); ordinal++) {
                var workshopPaintableId = "ws-" + paintableComponent.id() + "-" + String.format(Locale.ROOT, "%03d", ordinal);
                if (existingIds.contains(workshopPaintableId)) continue;
                var displayName = paintableComponent.quantity() == 1
                        ? paintableComponent.name()
                        : paintableComponent.name() + " #" + ordinal;
                var workshopPaintable = WorkshopPaintable.create(
                        workshopPaintableId, paintableComponent.id(), paintingProjectId, displayName, ordinal, occurredAt);
                events.addAll(envelopeFactory.envelop(
                        workshopPaintable, actor, correlationId, null, baseKey + ":" + workshopPaintableId, recordedAt));
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
        require(command.paintableComponentId(), "paintableComponentId");
        require(command.displayName(), "displayName");
        if (command.version() < 1) throw new DomainException("invalid_input", "version must be positive.");
        if (command.solutions().isEmpty()) throw new DomainException("invalid_input", "At least one recipe solution is required.");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var paintableProductId = paintableProductIdForPaintableComponent(snapshot, command.paintableComponentId());
        var paintingProject = PaintingProjectProjector.project(snapshot.events()).stream()
                .filter(project -> paintableProductId.equals(project.paintableProductId())).findFirst().orElse(null);
        if (paintingProject == null) {
            throw new DomainException("conflict", "Paintable product is not imported in the workshop: " + paintableProductId);
        }
        var recipes = WorkshopRecipeProjector.project(snapshot.events());
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var recipeId = present(command.recipeId()) ? command.recipeId()
                : "recipe-" + command.paintableComponentId() + "-v" + command.version() + "-" + Ulid.next(occurredAt).toLowerCase(Locale.ROOT);
        if (recipes.stream().anyMatch(recipe -> recipeId.equals(recipe.id()))) {
            throw new DomainException("conflict", "Workshop recipe already exists: " + recipeId);
        }
        MarketPaintingGuide guide = null;
        if (present(command.basedOnGuideId())) {
            guide = queries.marketCatalog(snapshot).paintingGuides().stream()
                    .filter(candidate -> command.basedOnGuideId().equals(candidate.id()))
                    .findFirst().orElseThrow(() -> new DomainException("not_found", "Market painting guide not found: " + command.basedOnGuideId()));
            if (!command.paintableComponentId().equals(guide.paintableComponentId())) {
                throw new DomainException("conflict", "Market guide and workshop recipe must target the same paintable component.");
            }
        }
        if (present(command.supersedesRecipeId())) {
            var previous = recipes.stream().filter(recipe -> command.supersedesRecipeId().equals(recipe.id())).findFirst()
                    .orElseThrow(() -> new DomainException("not_found", "Superseded recipe not found: " + command.supersedesRecipeId()));
            if (!command.paintableComponentId().equals(previous.paintableComponentId()) || command.version() != previous.version() + 1) {
                throw new DomainException("conflict", "A recipe revision must target the same paintable component and increment the version by one.");
            }
        }
        validateRecipeSolutions(command.solutions(), guide, snapshot);
        var recipe = WorkshopRecipe.create(
                recipeId, paintingProject.id(), command.paintableComponentId(), command.basedOnGuideId(),
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
        require(command.workshopPaintableId(), "workshopPaintableId");
        require(command.recipeId(), "recipeId");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var item = WorkshopPaintableProjector.project(snapshot.events()).stream()
                .filter(candidate -> command.workshopPaintableId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop paintable not found: " + command.workshopPaintableId()));
        var recipe = WorkshopRecipeProjector.project(snapshot.events()).stream()
                .filter(candidate -> command.recipeId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop recipe not found: " + command.recipeId()));
        if (recipe.status() != WorkshopRecipeStatus.ACTIVE) {
            throw new DomainException("conflict", "Only an active workshop recipe can be assigned.");
        }
        if (!item.paintableComponentId().equals(recipe.paintableComponentId())) {
            throw new DomainException("conflict", "Workshop paintable and recipe must target the same paintable component.");
        }
        var workshopPaintable = workshopPaintableAggregate(snapshot, item.id());
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        workshopPaintable.assignRecipe(recipe.id(), recipe.version(), occurredAt);
        return publish(workshopPaintable,
                new Actor("user", defaultText(command.actorId(), "owner")),
                command.correlationId(), command.idempotencyKey());
    }

    public synchronized PublicationReceipt addWorkshopPaintable(AddWorkshopPaintableCommand command) {
        require(command.paintableComponentId(), "paintableComponentId");
        require(command.paintingProjectId(), "paintingProjectId");
        require(command.displayName(), "displayName");
        var snapshot = snapshots.load();
        var paintingProject = paintingProject(snapshot, command.paintingProjectId());
        var product = queries.findProduct(snapshot, paintingProject.paintableProductId());
        if (WorkshopProjector.project(snapshot.events())
                .filter(workshop -> workshop.containsPaintingProject(paintingProject.id())).isEmpty()) {
            throw new DomainException("conflict", "Painting project is not registered in the workshop: " + paintingProject.id());
        }
        product.paintableComponent(command.paintableComponentId());
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var workshopPaintableId = present(command.workshopPaintableId()) ? command.workshopPaintableId() : "ws-" + command.paintableComponentId() + "-" + Ulid.next(occurredAt).toLowerCase(Locale.ROOT);
        if (snapshot.events().stream().anyMatch(event -> workshopPaintableId.equals(event.aggregateId()) && "workshop_item.added".equals(event.eventType()))) {
            throw new DomainException("conflict", "Workshop paintable already exists: " + workshopPaintableId);
        }
        var item = WorkshopPaintable.create(
                workshopPaintableId, command.paintableComponentId(), paintingProject.id(), command.displayName(), 0, occurredAt);
        return publish(item,
                new Actor("user", defaultText(command.actorId(), "owner")),
                command.correlationId(), command.idempotencyKey());
    }

    public synchronized PublicationReceipt transitionWorkshopPaintableStage(TransitionWorkshopPaintableStageCommand command) {
        require(command.workshopPaintableId(), "workshopPaintableId");
        var stage = WorkflowStage.fromId(command.stage());
        var action = StageAction.fromId(command.action());
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var item = WorkshopPaintableProjector.project(snapshot.events()).stream().filter(candidate -> command.workshopPaintableId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop paintable not found: " + command.workshopPaintableId()));
        var workshopPaintable = workshopPaintableAggregate(snapshot, item.id());
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var note = action == StageAction.SKIP ? command.reason() : command.comment();
        workshopPaintable.transition(stage, action, note, occurredAt);
        return publish(workshopPaintable,
                new Actor("user", defaultText(command.actorId(), "owner")),
                command.correlationId(), command.idempotencyKey());
    }

    public synchronized PublicationReceipt addWorkshopPaintableComment(AddWorkshopPaintableCommentCommand command) {
        require(command.workshopPaintableId(), "workshopPaintableId");
        require(command.comment(), "comment");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var item = WorkshopPaintableProjector.project(snapshot.events()).stream()
                .filter(candidate -> command.workshopPaintableId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop paintable not found: " + command.workshopPaintableId()));
        var workshopPaintable = workshopPaintableAggregate(snapshot, item.id());
        workshopPaintable.addComment(command.comment().trim(),
                command.occurredAt() == null ? Instant.now() : command.occurredAt());
        return publish(workshopPaintable,
                new Actor("user", defaultText(command.actorId(), "owner")),
                command.correlationId(), command.idempotencyKey());
    }

    public synchronized PublicationReceipt addWorkshopPaintablePhoto(AddWorkshopPaintablePhotoCommand command) {
        require(command.workshopPaintableId(), "workshopPaintableId");
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
        var item = WorkshopPaintableProjector.project(snapshot.events()).stream()
                .filter(candidate -> command.workshopPaintableId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop paintable not found: " + command.workshopPaintableId()));
        var stage = present(command.stage()) ? WorkflowStage.fromId(command.stage()) : null;
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var mediaId = "media-" + Ulid.next(occurredAt).toLowerCase(Locale.ROOT);
        var stored = mediaStorage.store(item.id(), mediaId, command.originalFilename(), contentType, content);
        var workshopPaintable = workshopPaintableAggregate(snapshot, item.id());
        workshopPaintable.addPhoto(
                stored.id(), stored.publicPath(), stage, command.caption(), stored.originalFilename(),
                stored.contentType(), stored.size(), stored.sha256(), occurredAt);
        try {
            return publish(workshopPaintable,
                    new Actor("user", defaultText(command.actorId(), "owner")),
                    command.correlationId(), command.idempotencyKey());
        } catch (RuntimeException failure) {
            mediaStorage.delete(stored);
            throw failure;
        }
    }

    public synchronized PublicationReceipt setShoppingListEntryChecked(SetShoppingListEntryCheckedCommand command) {
        require(command.shoppingListEntryId(), "shoppingListEntryId");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return existingReceipt(duplicate);
        var known = queries.shoppingViews(snapshot).stream().anyMatch(item -> command.shoppingListEntryId().equals(item.id()));
        if (!known) throw new DomainException("not_found", "Shopping list entry not found: " + command.shoppingListEntryId());
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var itemHistory = snapshot.events().stream()
                .filter(event -> command.shoppingListEntryId().equals(event.aggregateId()))
                .map(EventEnvelope::event)
                .filter(ShoppingListEntryEvent.class::isInstance)
                .map(ShoppingListEntryEvent.class::cast)
                .toList();
        var currentChecked = itemHistory.stream()
                .filter(ShoppingListEntryCheckedChanged.class::isInstance)
                .map(ShoppingListEntryCheckedChanged.class::cast)
                .reduce((left, right) -> right)
                .map(ShoppingListEntryCheckedChanged::checked)
                .orElse(false);
        var shoppingListEntry = ShoppingListEntry.current(command.shoppingListEntryId(), currentChecked, itemHistory);
        shoppingListEntry.setChecked(command.checked(), occurredAt);
        if (shoppingListEntry.pendingEvents().isEmpty()) {
            return snapshot.events().stream()
                    .filter(event -> command.shoppingListEntryId().equals(event.aggregateId()))
                    .reduce((left, right) -> right)
                    .map(WorkshopCommandService::existingReceipt)
                    .orElseThrow(() -> new DomainException("no_change", "Shopping list entry checked state is already current."));
        }
        return publish(shoppingListEntry,
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

    private static PaintingProject paintingProjectForProduct(DataSnapshot snapshot, String paintableProductId) {
        return PaintingProjectProjector.project(snapshot.events()).stream()
                .filter(project -> paintableProductId.equals(project.paintableProductId())).findFirst()
                .orElseThrow(() -> new DomainException(
                        "conflict", "Paintable product is not part of a painting project: " + paintableProductId));
    }

    private static String paintableProductIdForPaintableComponent(DataSnapshot snapshot, String paintableComponentId) {
        for (var product : snapshot.paintableProducts()) {
            if (product.paintableComponents().stream().anyMatch(item -> paintableComponentId.equals(item.id()))) {
                return product.id();
            }
        }
        throw new DomainException("not_found", "Paintable component not found: " + paintableComponentId);
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

    private WorkshopPaintable workshopPaintableAggregate(DataSnapshot snapshot, String workshopPaintableId) {
        var history = snapshot.events().stream()
                .filter(event -> workshopPaintableId.equals(event.aggregateId()))
                .map(EventEnvelope::event)
                .filter(WorkshopPaintableEvent.class::isInstance)
                .map(WorkshopPaintableEvent.class::cast)
                .toList();
        if (history.isEmpty()) {
            throw new DomainException("not_found", "Workshop paintable not found: " + workshopPaintableId);
        }
        return WorkshopPaintable.rehydrate(history);
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
        var ownedPaintIds = snapshot.paintInventory().availablePaintProductIds();
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
