package com.minipaintdex.application.command;

import java.time.Instant;

public record TransitionWorkshopRecipeCommand(
        String recipeId,
        String action,
        String successorRecipeId,
        String reason,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {
}
