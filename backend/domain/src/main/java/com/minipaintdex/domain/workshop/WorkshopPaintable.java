package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventSourcedAggregateRoot;
import com.minipaintdex.domain.shared.DomainException;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Aggregate root that owns the progress, notes, photos and recipe of one physical miniature. */
public final class WorkshopPaintable extends EventSourcedAggregateRoot {
    private String id;
    private String paintableComponentId;
    private String paintingProjectId;
    private String displayName;
    private int ordinal;
    private final EnumMap<WorkflowStage, WorkflowStageStatus> workflow =
            new EnumMap<>(WorkflowStage.class);
    private String recipeId;
    private int recipeVersion;
    private Instant updatedAt;

    private WorkshopPaintable() {}

    public static WorkshopPaintable create(
            String id, String paintableComponentId, String paintingProjectId,
            String displayName, int ordinal, Instant occurredAt) {
        var item = new WorkshopPaintable();
        item.raise(new WorkshopPaintableAdded(
                id, paintableComponentId, paintingProjectId, displayName, ordinal, occurredAt));
        return item;
    }

    public static WorkshopPaintable rehydrate(List<? extends WorkshopPaintableEvent> history) {
        var item = new WorkshopPaintable();
        item.replayHistory(history, WorkshopPaintableAdded.class, "workshop_item");
        return item;
    }

    public void transition(WorkflowStage stage, StageAction action, String note, Instant occurredAt) {
        if (stage == null || action == null) {
            throw new DomainException("invalid_transition", "Workflow stage and action are required.");
        }
        switch (action) {
            case START -> raise(new WorkflowStageStarted(id, paintingProjectId, stage, note, occurredAt));
            case COMPLETE -> raise(new WorkflowStageCompleted(id, paintingProjectId, stage, note, occurredAt));
            case SKIP -> raise(new WorkflowStageSkipped(id, paintingProjectId, stage,
                    DomainFields.required(note, "skip reason"), occurredAt));
            case REOPEN -> raise(new WorkflowStageReopened(id, paintingProjectId, stage, note, occurredAt));
        }
    }

    public void addComment(String comment, Instant occurredAt) {
        raise(new WorkshopPaintableCommentAdded(id, paintingProjectId, comment, occurredAt));
    }

    public void addPhoto(
            String mediaId, String url, WorkflowStage stage, String caption, String originalFilename,
            String contentType, long size, String sha256, Instant occurredAt) {
        raise(new WorkshopPaintablePhotoAdded(id, paintingProjectId, mediaId, url, stage, caption,
                originalFilename, contentType, size, sha256, occurredAt));
    }

    public void assignRecipe(String recipeId, int recipeVersion, Instant occurredAt) {
        raise(new WorkshopPaintableRecipeAssigned(id, paintingProjectId, recipeId, recipeVersion, occurredAt));
    }

    @Override
    protected void apply(DomainEvent event) {
        switch (event) {
            case WorkshopPaintableAdded added -> {
                id = added.workshopPaintableId();
                paintableComponentId = added.paintableComponentId();
                paintingProjectId = added.paintingProjectId();
                displayName = added.displayName();
                ordinal = added.ordinal();
                workflow.clear();
                for (var stage : WorkflowStage.values()) workflow.put(stage, WorkflowStageStatus.PENDING);
                updatedAt = added.occurredAt();
            }
            case WorkshopPaintableRecipeAssigned assigned -> {
                recipeId = assigned.recipeId();
                recipeVersion = assigned.recipeVersion();
                updatedAt = assigned.occurredAt();
            }
            case WorkflowStageStarted started -> {
                assertTransition(started.stage(), StageAction.START);
                applyStage(started.stage(), WorkflowStageStatus.IN_PROGRESS, started.occurredAt());
            }
            case WorkflowStageCompleted completed -> {
                assertTransition(completed.stage(), StageAction.COMPLETE);
                applyStage(completed.stage(), WorkflowStageStatus.COMPLETED, completed.occurredAt());
            }
            case WorkflowStageSkipped skipped -> {
                assertTransition(skipped.stage(), StageAction.SKIP);
                applyStage(skipped.stage(), WorkflowStageStatus.SKIPPED, skipped.occurredAt());
            }
            case WorkflowStageReopened reopened -> {
                assertTransition(reopened.stage(), StageAction.REOPEN);
                applyStage(reopened.stage(), WorkflowStageStatus.IN_PROGRESS, reopened.occurredAt());
            }
            case WorkshopPaintableCommentAdded comment -> updatedAt = comment.occurredAt();
            case WorkshopPaintablePhotoAdded photo -> updatedAt = photo.occurredAt();
            default -> throw new DomainException("invalid_workshop_item_event",
                    "Unsupported workshop paintable event: " + event.eventType());
        }
    }

    private void assertTransition(WorkflowStage stage, StageAction action) {
        if (stage == null || action == null) {
            throw new DomainException("invalid_transition", "Workflow stage and action are required.");
        }
        if (action == StageAction.SKIP && !stage.skippable()) {
            throw new DomainException("invalid_transition", "Workflow stage " + stage.id() + " cannot be skipped.");
        }
        var current = workflow.get(stage);
        var valid = switch (action) {
            case START -> current == WorkflowStageStatus.PENDING;
            case COMPLETE, SKIP -> current == WorkflowStageStatus.PENDING || current == WorkflowStageStatus.IN_PROGRESS;
            case REOPEN -> current == WorkflowStageStatus.COMPLETED || current == WorkflowStageStatus.SKIPPED;
        };
        if (!valid) {
            throw new DomainException("invalid_transition",
                    "Cannot " + action.id() + " a stage currently marked " + current.id() + ".");
        }
        if (action == StageAction.REOPEN) return;
        for (var previous : WorkflowStage.values()) {
            if (previous == stage) break;
            var previousStatus = workflow.get(previous);
            if (previousStatus != WorkflowStageStatus.COMPLETED
                    && previousStatus != WorkflowStageStatus.SKIPPED) {
                throw new DomainException("invalid_transition",
                        "Cannot " + action.id() + " stage " + stage.id()
                                + " before stage " + previous.id() + " is completed or skipped.");
            }
        }
    }

    private void applyStage(WorkflowStage stage, WorkflowStageStatus status, Instant occurredAt) {
        workflow.put(stage, status);
        updatedAt = occurredAt;
    }

    @Override public String id() { return id; }
    public String paintableComponentId() { return paintableComponentId; }
    public String paintingProjectId() { return paintingProjectId; }
    public String displayName() { return displayName; }
    public int ordinal() { return ordinal; }
    public Map<WorkflowStage, WorkflowStageStatus> workflow() {
        return Collections.unmodifiableMap(new EnumMap<>(workflow));
    }
    public WorkflowStage currentStage() {
        for (var stage : WorkflowStage.values()) if (workflow.get(stage) == WorkflowStageStatus.IN_PROGRESS) return stage;
        for (var stage : WorkflowStage.values()) if (workflow.get(stage) == WorkflowStageStatus.PENDING) return stage;
        return null;
    }
    public boolean completed() {
        return !workflow.isEmpty() && workflow.values().stream().allMatch(status ->
                status == WorkflowStageStatus.COMPLETED || status == WorkflowStageStatus.SKIPPED);
    }
    public String recipeId() { return recipeId; }
    public int recipeVersion() { return recipeVersion; }
    public Instant updatedAt() { return updatedAt; }
}
