package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopRecipeSuperseded(
        String recipeId, String paintingProjectId, String successorRecipeId,
        Instant occurredAt) implements WorkshopRecipeEvent {
    public WorkshopRecipeSuperseded {
        recipeId = DomainFields.required(recipeId, "recipeId");
        paintingProjectId = DomainFields.required(paintingProjectId, "paintingProjectId");
        successorRecipeId = DomainFields.required(successorRecipeId, "successorRecipeId");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "workshop_recipe.superseded"; }
    @Override public String aggregateId() { return recipeId; }
    @Override public String projectId() { return paintingProjectId; }
}
