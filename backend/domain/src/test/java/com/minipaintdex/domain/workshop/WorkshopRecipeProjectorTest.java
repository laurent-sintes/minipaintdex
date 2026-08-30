package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.workflow.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkshopRecipeProjectorTest {
    @Test
    void projectsTheIndependentRecipeLifecycle() {
        var created = event("workshop_recipe.created", Map.of(
                "catalog_item_id", "game-hero", "display_name", "My hero", "version", 1, "solutions", List.of()));
        var validated = event("workshop_recipe.validated", Map.of());
        var activated = event("workshop_recipe.activated", Map.of());

        var state = WorkshopRecipeProjector.project(List.of(created, validated, activated)).getFirst();

        assertEquals(WorkshopRecipeStatus.ACTIVE, state.status());
        assertEquals("game-hero", state.catalogItemId());
        assertEquals(1, state.version());
    }

    @Test
    void rejectsSkippingLifecycleValidation() {
        assertThrows(DomainException.class, () -> WorkshopRecipeProjector.assertTransition(
                WorkshopRecipeStatus.DRAFT, "activate"));
    }

    private static DomainEvent event(String type, Map<String, Object> payload) {
        var instant = Instant.parse("2026-08-30T10:00:00Z");
        return new DomainEvent(type + "-id", 1, type, instant, instant, "workshop_recipe", "recipe-1", "game",
                new Actor("user", "owner"), "correlation", null, null, payload);
    }
}
