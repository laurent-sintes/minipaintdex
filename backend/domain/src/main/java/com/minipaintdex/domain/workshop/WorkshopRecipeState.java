package com.minipaintdex.domain.workshop;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record WorkshopRecipeState(
        String id,
        String catalogItemId,
        String basedOnGuideId,
        String supersedesRecipeId,
        String displayName,
        int version,
        WorkshopRecipeStatus status,
        List<Map<String, Object>> solutions,
        Instant updatedAt) {

    public WorkshopRecipeState {
        solutions = solutions == null ? List.of() : List.copyOf(solutions);
    }
}
