package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopRecipeActivated(
        String recipeId, String paintingProjectId, Instant occurredAt) implements WorkshopRecipeEvent {
    public WorkshopRecipeActivated {
        recipeId = DomainFields.id(recipeId, "recipeId");
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "workshop_recipe.activated"; }
    @Override public String aggregateId() { return recipeId; }
    @Override public String projectId() { return paintingProjectId; }
}
