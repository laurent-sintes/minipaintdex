package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

public final class WorkshopProjector {
    private WorkshopProjector() {}

    public static Workshop project(List<DomainEvent> events) {
        var paintingProjectIds = new LinkedHashSet<String>();
        Instant updatedAt = null;
        for (var event : events) {
            if ("workshop.created".equals(event.eventType()) && Workshop.DEFAULT_ID.equals(event.aggregateId())) {
                updatedAt = event.recordedAt();
            }
            if ("painting_project.created".equals(event.eventType())
                    && Workshop.DEFAULT_ID.equals(text(event.payload().get("workshop_id")))) {
                paintingProjectIds.add(event.aggregateId());
                updatedAt = event.recordedAt();
            }
        }
        return new Workshop(Workshop.DEFAULT_ID, List.copyOf(paintingProjectIds), updatedAt);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
