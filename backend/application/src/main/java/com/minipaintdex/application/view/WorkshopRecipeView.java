package com.minipaintdex.application.view;

import com.minipaintdex.domain.workshop.RecipeSolution;

import java.time.Instant;
import java.util.List;

/** Personal paint recipe with a lifecycle independent from its market guide. */
public record WorkshopRecipeView(
        String id,
        String catalogItemId,
        String basedOnGuideId,
        String supersedesRecipeId,
        String displayName,
        int version,
        String status,
        List<RecipeSolution> solutions,
        Instant updatedAt) {
    public WorkshopRecipeView { solutions = List.copyOf(solutions); }
}
