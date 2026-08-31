package com.minipaintdex.domain.workshop;

import java.time.Instant;
import java.util.Objects;

public record WorkflowStageSkipped(
        String workshopItemId, String paintingProjectId, WorkflowStage stage,
        String reason, Instant occurredAt) implements WorkshopItemEvent {
    public WorkflowStageSkipped {
        workshopItemId = DomainFields.id(workshopItemId, "workshopItemId");
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        stage = Objects.requireNonNull(stage, "stage is required.");
        reason = DomainFields.required(reason, "reason");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }

    @Override public String eventType() { return "workflow.stage.skipped"; }
    @Override public String aggregateId() { return workshopItemId; }
    @Override public String projectId() { return paintingProjectId; }
}
