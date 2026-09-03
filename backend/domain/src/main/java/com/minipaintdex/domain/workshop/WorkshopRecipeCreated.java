package com.minipaintdex.domain.workshop;

import java.time.Instant;
import java.util.List;

public record WorkshopRecipeCreated(
        String recipeId,
        String paintingProjectId,
        String paintableComponentId,
        String basedOnGuideId,
        String supersedesRecipeId,
        String displayName,
        int recipeVersion,
        List<RecipeSolution> solutions,
        Instant occurredAt) implements WorkshopRecipeEvent {
    public WorkshopRecipeCreated {
        recipeId = DomainFields.id(recipeId, "recipeId");
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        paintableComponentId = DomainFields.id(paintableComponentId, "paintableComponentId");
        if (basedOnGuideId != null && !basedOnGuideId.isBlank()) {
            basedOnGuideId = DomainFields.id(basedOnGuideId, "basedOnGuideId");
        }
        if (supersedesRecipeId != null && !supersedesRecipeId.isBlank()) {
            supersedesRecipeId = DomainFields.id(supersedesRecipeId, "supersedesRecipeId");
        }
        basedOnGuideId = DomainFields.optional(basedOnGuideId);
        supersedesRecipeId = DomainFields.optional(supersedesRecipeId);
        displayName = DomainFields.required(displayName, "displayName");
        if (recipeVersion < 1) throw DomainFields.invalid("recipeVersion must be positive.");
        solutions = solutions == null ? List.of() : List.copyOf(solutions);
        if (solutions.isEmpty()) throw DomainFields.invalid("At least one recipe solution is required.");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }

    @Override public String eventType() { return "workshop_recipe.created"; }
    @Override public String aggregateId() { return recipeId; }
    @Override public String scopePaintingProjectId() { return paintingProjectId; }
}
