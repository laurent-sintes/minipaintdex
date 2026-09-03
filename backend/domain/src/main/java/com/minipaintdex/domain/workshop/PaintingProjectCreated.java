package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record PaintingProjectCreated(
        String paintingProjectId, String workshopId, String paintableProductId,
        String name, int paintableCount, Instant occurredAt) implements PaintingProjectEvent {
    public PaintingProjectCreated {
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        workshopId = DomainFields.id(workshopId, "workshopId");
        paintableProductId = DomainFields.id(paintableProductId, "paintableProductId");
        name = DomainFields.required(name, "name");
        if (paintableCount < 1) throw DomainFields.invalid("paintableCount must be positive.");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "painting_project.created"; }
    @Override public String aggregateId() { return paintingProjectId; }
}
