package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopRecipeArchived(
        String recipeId, String paintingProjectId, String reason,
        Instant occurredAt) implements WorkshopRecipeEvent {
    public WorkshopRecipeArchived {
        recipeId = DomainFields.required(recipeId, "recipeId");
        paintingProjectId = DomainFields.required(paintingProjectId, "paintingProjectId");
        reason = DomainFields.optional(reason);
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "workshop_recipe.archived"; }
    @Override public String aggregateId() { return recipeId; }
    @Override public String projectId() { return paintingProjectId; }
}
