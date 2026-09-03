package com.minipaintdex.domain.workshop;

import java.time.Instant;
import java.util.Objects;

public record WorkflowStageSkipped(
        String workshopPaintableId, String paintingProjectId, WorkflowStage stage,
        String reason, Instant occurredAt) implements WorkshopPaintableEvent {
    public WorkflowStageSkipped {
        workshopPaintableId = DomainFields.id(workshopPaintableId, "workshopPaintableId");
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        stage = Objects.requireNonNull(stage, "stage is required.");
        reason = DomainFields.required(reason, "reason");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }

    @Override public String eventType() { return "workflow.stage.skipped"; }
    @Override public String aggregateId() { return workshopPaintableId; }
    @Override public String scopePaintingProjectId() { return paintingProjectId; }
}
