package com.minipaintdex.application.command;

import java.time.Instant;
import java.util.List;

import com.minipaintdex.domain.workshop.RecipeSolution;

public record CreateWorkshopRecipeCommand(
        String recipeId,
        String catalogItemId,
        String basedOnGuideId,
        String supersedesRecipeId,
        String displayName,
        int version,
        List<RecipeSolution> solutions,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {

    public CreateWorkshopRecipeCommand {
        solutions = solutions == null ? List.of() : List.copyOf(solutions);
    }
}
