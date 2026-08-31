package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopProjectorTest {
    @Test
    void projectsPaintingProjectMembership() {
        var now = Instant.parse("2026-08-30T10:00:00Z");
        var workshopCreated = new DomainEvent("01KTESTWORKSHOP000000000000", 1, "workshop.created", now, now,
                "workshop", "my-workshop", null, new Actor("user", "owner"), "current", null, null,
                Map.of("name", "My workshop"));
        var projectCreated = new DomainEvent("01KTESTPROJECT0000000000000", 1, "painting_project.created", now, now,
                "painting_project", "paint-game", "paint-game", new Actor("user", "owner"), "current", null, null,
                Map.of("workshop_id", "my-workshop", "paintable_product_id", "game", "name", "Paint Game"));

        var workshop = WorkshopProjector.project(List.of(workshopCreated, projectCreated));
        var projects = PaintingProjectProjector.project(List.of(workshopCreated, projectCreated));

        assertTrue(workshop.containsPaintingProject("paint-game"));
        assertEquals("game", projects.getFirst().paintableProductId());
    }
}
