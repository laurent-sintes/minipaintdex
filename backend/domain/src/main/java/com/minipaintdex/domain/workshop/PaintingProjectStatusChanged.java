package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record PaintingProjectStatusChanged(
        String paintingProjectId, PaintingProjectStatus status, Instant occurredAt) implements PaintingProjectEvent {
    public PaintingProjectStatusChanged {
        paintingProjectId = DomainFields.required(paintingProjectId, "paintingProjectId");
        if (status == null) throw DomainFields.invalid("status is required.");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "painting_project.status_changed"; }
    @Override public String aggregateId() { return paintingProjectId; }
}
