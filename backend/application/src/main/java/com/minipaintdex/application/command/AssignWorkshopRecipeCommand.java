package com.minipaintdex.application.command;

import java.time.Instant;

public record AssignWorkshopRecipeCommand(
        String workshopPaintableId,
        String recipeId,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {
}
