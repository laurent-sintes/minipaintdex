package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopRecipeSuperseded(
        String recipeId, String paintingProjectId, String successorRecipeId,
        Instant occurredAt) implements WorkshopRecipeEvent {
    public WorkshopRecipeSuperseded {
        recipeId = DomainFields.id(recipeId, "recipeId");
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        successorRecipeId = DomainFields.id(successorRecipeId, "successorRecipeId");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "workshop_recipe.superseded"; }
    @Override public String aggregateId() { return recipeId; }
    @Override public String scopePaintingProjectId() { return paintingProjectId; }
}
