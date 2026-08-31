package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopItemRecipeAssigned(
        String workshopItemId, String paintingProjectId, String recipeId,
        int recipeVersion, Instant occurredAt) implements WorkshopItemEvent {
    public WorkshopItemRecipeAssigned {
        workshopItemId = DomainFields.id(workshopItemId, "workshopItemId");
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        recipeId = DomainFields.id(recipeId, "recipeId");
        if (recipeVersion < 1) throw DomainFields.invalid("recipeVersion must be positive.");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "recipe.assigned"; }
    @Override public String aggregateId() { return workshopItemId; }
    @Override public String projectId() { return paintingProjectId; }
}
