package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class WorkshopRecipeProjector {
    private WorkshopRecipeProjector() {}

    public static List<WorkshopRecipeState> project(List<? extends DomainEvent> events) {
        var histories = new LinkedHashMap<String, List<WorkshopRecipeEvent>>();
        for (var candidate : events) {
            var event = candidate instanceof EventEnvelope envelope ? envelope.event() : candidate;
            if (event instanceof WorkshopRecipeEvent recipeEvent) {
                histories.computeIfAbsent(recipeEvent.aggregateId(), ignored -> new ArrayList<>()).add(recipeEvent);
            }
        }
        return histories.values().stream().map(WorkshopRecipe::rehydrate).map(recipe -> new WorkshopRecipeState(
                recipe.id(), recipe.paintableComponentId(), recipe.basedOnGuideId(), recipe.supersedesRecipeId(),
                recipe.displayName(), recipe.recipeVersion(), recipe.status(), recipe.solutions(), recipe.updatedAt())).toList();
    }
}
