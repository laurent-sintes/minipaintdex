package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopPaintableRecipeAssigned(
        String workshopPaintableId, String paintingProjectId, String recipeId,
        int recipeVersion, Instant occurredAt) implements WorkshopPaintableEvent {
    public WorkshopPaintableRecipeAssigned {
        workshopPaintableId = DomainFields.id(workshopPaintableId, "workshopPaintableId");
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        recipeId = DomainFields.id(recipeId, "recipeId");
        if (recipeVersion < 1) throw DomainFields.invalid("recipeVersion must be positive.");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "recipe.assigned"; }
    @Override public String aggregateId() { return workshopPaintableId; }
    @Override public String scopePaintingProjectId() { return paintingProjectId; }
}
