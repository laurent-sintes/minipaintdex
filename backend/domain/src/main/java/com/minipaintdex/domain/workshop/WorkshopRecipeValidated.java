package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopRecipeValidated(
        String recipeId, String paintingProjectId, Instant occurredAt) implements WorkshopRecipeEvent {
    public WorkshopRecipeValidated {
        recipeId = DomainFields.id(recipeId, "recipeId");
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "workshop_recipe.validated"; }
    @Override public String aggregateId() { return recipeId; }
    @Override public String scopePaintingProjectId() { return paintingProjectId; }
}
