package com.minipaintdex.domain.workshop;

import java.time.Instant;
import java.util.Objects;

public record WorkflowStageCompleted(
        String workshopPaintableId, String paintingProjectId, WorkflowStage stage,
        String comment, Instant occurredAt) implements WorkshopPaintableEvent {
    public WorkflowStageCompleted {
        workshopPaintableId = DomainFields.id(workshopPaintableId, "workshopPaintableId");
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        stage = Objects.requireNonNull(stage, "stage is required.");
        comment = DomainFields.optional(comment);
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }

    @Override public String eventType() { return "workflow.stage.completed"; }
    @Override public String aggregateId() { return workshopPaintableId; }
    @Override public String scopePaintingProjectId() { return paintingProjectId; }
}
