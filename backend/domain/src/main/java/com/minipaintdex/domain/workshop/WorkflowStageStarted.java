package com.minipaintdex.domain.workshop;

import java.time.Instant;
import java.util.Objects;

public record WorkflowStageStarted(
        String workshopItemId, String paintingProjectId, WorkflowStage stage,
        String comment, Instant occurredAt) implements WorkshopItemEvent {
    public WorkflowStageStarted {
        workshopItemId = DomainFields.required(workshopItemId, "workshopItemId");
        paintingProjectId = DomainFields.required(paintingProjectId, "paintingProjectId");
        stage = Objects.requireNonNull(stage, "stage is required.");
        comment = DomainFields.optional(comment);
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }

    @Override public String eventType() { return "workflow.stage.started"; }
    @Override public String aggregateId() { return workshopItemId; }
    @Override public String projectId() { return paintingProjectId; }
}
