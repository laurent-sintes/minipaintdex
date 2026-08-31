package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventSourcedAggregateRoot;
import com.minipaintdex.domain.shared.DomainException;

import java.time.Instant;
import java.util.List;

/** Aggregate root for the owner's versioned recipe for one catalog miniature. */
public final class WorkshopRecipe extends EventSourcedAggregateRoot {
    private String id;
    private String paintingProjectId;
    private String catalogItemId;
    private String basedOnGuideId;
    private String supersedesRecipeId;
    private String successorRecipeId;
    private String displayName;
    private int recipeVersion;
    private WorkshopRecipeStatus status;
    private List<RecipeSolution> solutions = List.of();
    private Instant updatedAt;

    private WorkshopRecipe() {}

    public static WorkshopRecipe create(
            String id, String paintingProjectId, String catalogItemId,
            String basedOnGuideId, String supersedesRecipeId, String displayName,
            int recipeVersion, List<RecipeSolution> solutions, Instant occurredAt) {
        var recipe = new WorkshopRecipe();
        recipe.raise(new WorkshopRecipeCreated(id, paintingProjectId, catalogItemId,
                basedOnGuideId, supersedesRecipeId, displayName, recipeVersion, solutions, occurredAt));
        return recipe;
    }

    public static WorkshopRecipe rehydrate(List<? extends WorkshopRecipeEvent> history) {
        var recipe = new WorkshopRecipe();
        history.forEach(recipe::replay);
        return recipe;
    }

    public void validate(Instant occurredAt) {
        requireStatus(WorkshopRecipeStatus.DRAFT, "validate");
        raise(new WorkshopRecipeValidated(id, paintingProjectId, occurredAt));
    }

    public void activate(Instant occurredAt) {
        requireStatus(WorkshopRecipeStatus.VALIDATED, "activate");
        raise(new WorkshopRecipeActivated(id, paintingProjectId, occurredAt));
    }

    public void supersede(String successorRecipeId, Instant occurredAt) {
        requireStatus(WorkshopRecipeStatus.ACTIVE, "supersede");
        raise(new WorkshopRecipeSuperseded(id, paintingProjectId, successorRecipeId, occurredAt));
    }

    public void archive(String reason, Instant occurredAt) {
        if (status == WorkshopRecipeStatus.ARCHIVED) {
            throw invalidTransition("archive");
        }
        raise(new WorkshopRecipeArchived(id, paintingProjectId, reason, occurredAt));
    }

    @Override
    protected void apply(DomainEvent event) {
        switch (event) {
            case WorkshopRecipeCreated created -> {
                id = created.recipeId();
                paintingProjectId = created.paintingProjectId();
                catalogItemId = created.catalogItemId();
                basedOnGuideId = created.basedOnGuideId();
                supersedesRecipeId = created.supersedesRecipeId();
                displayName = created.displayName();
                recipeVersion = created.recipeVersion();
                status = WorkshopRecipeStatus.DRAFT;
                solutions = created.solutions();
                updatedAt = created.occurredAt();
            }
            case WorkshopRecipeValidated validated -> updateStatus(WorkshopRecipeStatus.VALIDATED, validated.occurredAt());
            case WorkshopRecipeActivated activated -> updateStatus(WorkshopRecipeStatus.ACTIVE, activated.occurredAt());
            case WorkshopRecipeSuperseded superseded -> {
                successorRecipeId = superseded.successorRecipeId();
                updateStatus(WorkshopRecipeStatus.SUPERSEDED, superseded.occurredAt());
            }
            case WorkshopRecipeArchived archived -> updateStatus(WorkshopRecipeStatus.ARCHIVED, archived.occurredAt());
            default -> throw new DomainException("invalid_workshop_recipe_event",
                    "Unsupported workshop recipe event: " + event.eventType());
        }
    }

    private void requireStatus(WorkshopRecipeStatus expected, String action) {
        if (status != expected) throw invalidTransition(action);
    }

    private DomainException invalidTransition(String action) {
        return new DomainException("invalid_transition",
                "Cannot " + action + " a workshop recipe currently marked " + status.id() + ".");
    }

    private void updateStatus(WorkshopRecipeStatus newStatus, Instant occurredAt) {
        status = newStatus;
        updatedAt = occurredAt;
    }

    @Override public String id() { return id; }
    public String paintingProjectId() { return paintingProjectId; }
    public String catalogItemId() { return catalogItemId; }
    public String basedOnGuideId() { return basedOnGuideId; }
    public String supersedesRecipeId() { return supersedesRecipeId; }
    public String successorRecipeId() { return successorRecipeId; }
    public String displayName() { return displayName; }
    public int recipeVersion() { return recipeVersion; }
    public WorkshopRecipeStatus status() { return status; }
    public List<RecipeSolution> solutions() { return solutions; }
    public Instant updatedAt() { return updatedAt; }
}
