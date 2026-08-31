package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkshopRecipeProjectorTest {
    private static final Instant AT = Instant.parse("2026-08-30T10:00:00Z");

    @Test
    void aggregateOwnsAndProjectsTheRecipeLifecycle() {
        var recipe = WorkshopRecipe.create(
                "recipe-1", "paint-game", "game-hero", null, null, "My hero", 1,
                List.of(new RecipeSolution(RecipeSolutionType.SINGLE_PAINT, null, "paint-1", List.of(), null)), AT);
        recipe.validate(AT.plusSeconds(1));
        recipe.activate(AT.plusSeconds(2));

        var state = WorkshopRecipeProjector.project(recipe.releaseEvents()).getFirst();
        assertEquals(WorkshopRecipeStatus.ACTIVE, state.status());
        assertEquals("game-hero", state.catalogItemId());
        assertEquals(1, state.version());
    }

    @Test
    void aggregateRejectsActivationBeforeValidation() {
        var recipe = WorkshopRecipe.create(
                "recipe-1", "paint-game", "game-hero", null, null, "My hero", 1,
                List.of(new RecipeSolution(RecipeSolutionType.SINGLE_PAINT, null, "paint-1", List.of(), null)), AT);
        assertThrows(DomainException.class, () -> recipe.activate(AT));
    }
}
