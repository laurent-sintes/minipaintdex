package com.minipaintdex.domain.workshop;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopProjectorTest {
    @Test
    void membershipIsEmittedByTheWorkshopAggregate() {
        var at = Instant.parse("2026-08-30T10:00:00Z");
        var workshop = Workshop.create("my-workshop", "My workshop", at);
        var project = PaintingProject.create("paint-game", "my-workshop", "game", "Paint Game", 1, at);
        project.changeStatus(PaintingProjectStatus.ACTIVE, at);
        workshop.registerPaintingProject(project.id(), at);
        var events = new ArrayList<>(workshop.releaseEvents());
        events.addAll(project.releaseEvents());

        var projectedWorkshop = WorkshopProjector.project(events).orElseThrow();
        var projectedProject = PaintingProjectProjector.project(events).getFirst();
        assertTrue(projectedWorkshop.containsPaintingProject("paint-game"));
        assertEquals("game", projectedProject.paintableProductId());
        assertEquals(PaintingProjectStatus.ACTIVE, projectedProject.status());
    }
}
