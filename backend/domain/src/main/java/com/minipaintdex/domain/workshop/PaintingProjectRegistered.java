package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record PaintingProjectRegistered(
        String workshopId, String paintingProjectId, Instant occurredAt) implements WorkshopEvent {
    public PaintingProjectRegistered {
        workshopId = DomainFields.id(workshopId, "workshopId");
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }

    @Override public String eventType() { return "workshop.painting_project_registered"; }
    @Override public String aggregateId() { return workshopId; }
}
