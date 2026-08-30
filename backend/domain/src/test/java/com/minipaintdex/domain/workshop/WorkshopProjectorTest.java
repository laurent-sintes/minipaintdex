package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopProjectorTest {
    @Test
    void projectsCurrentAndLegacyProductImports() {
        var now = Instant.parse("2026-08-30T10:00:00Z");
        var current = new DomainEvent("01KTESTCURRENT000000000000", 1, "workshop.product_imported", now, now,
                "workshop", "my-workshop", null, new Actor("user", "owner"), "current", null, null,
                Map.of("product_id", "current-product"));
        var legacy = new DomainEvent("01KTESTLEGACY0000000000000", 1, "project.created", now, now,
                "project", "legacy-product", "legacy-product", new Actor("migration", "legacy"), "legacy", null, null,
                Map.of("market_game_id", "legacy-product"));

        var workshop = WorkshopProjector.project(List.of(current, legacy));

        assertTrue(workshop.containsProduct("current-product"));
        assertTrue(workshop.containsProduct("legacy-product"));
    }
}
