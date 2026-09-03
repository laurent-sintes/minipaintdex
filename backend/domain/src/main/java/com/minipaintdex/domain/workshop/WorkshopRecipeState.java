package com.minipaintdex.domain.workshop;

import java.time.Instant;
import java.util.List;

public record WorkshopRecipeState(
        String id,
        String paintableComponentId,
        String basedOnGuideId,
        String supersedesRecipeId,
        String displayName,
        int version,
        WorkshopRecipeStatus status,
        List<RecipeSolution> solutions,
        Instant updatedAt) {

    public WorkshopRecipeState {
        solutions = solutions == null ? List.of() : List.copyOf(solutions);
    }
}
