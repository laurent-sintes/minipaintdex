package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record PaintingProjectCreated(
        String paintingProjectId, String workshopId, String paintableProductId,
        String name, int paintableItemCount, Instant occurredAt) implements PaintingProjectEvent {
    public PaintingProjectCreated {
        paintingProjectId = DomainFields.required(paintingProjectId, "paintingProjectId");
        workshopId = DomainFields.required(workshopId, "workshopId");
        paintableProductId = DomainFields.required(paintableProductId, "paintableProductId");
        name = DomainFields.required(name, "name");
        if (paintableItemCount < 1) throw DomainFields.invalid("paintableItemCount must be positive.");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "painting_project.created"; }
    @Override public String aggregateId() { return paintingProjectId; }
}
