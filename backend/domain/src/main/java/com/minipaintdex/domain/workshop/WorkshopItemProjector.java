package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.workflow.DomainException;
import com.minipaintdex.domain.workflow.StageAction;
import com.minipaintdex.domain.workflow.WorkflowStage;
import com.minipaintdex.domain.workflow.WorkflowStageStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkshopItemProjector {
    private WorkshopItemProjector() {}

    public static List<WorkshopItemState> project(List<DomainEvent> events) {
        var states = new LinkedHashMap<String, WorkshopItemState>();
        for (var event : events) {
            if ("workshop_item.added".equals(event.eventType())) {
                var paintingProjectId = text(event.payload().get("painting_project_id"));
                if (paintingProjectId.isBlank()) paintingProjectId = event.projectId();
                states.put(event.aggregateId(), new WorkshopItemState(
                        event.aggregateId(), text(event.payload().get("catalog_item_id")), paintingProjectId,
                        text(event.payload().getOrDefault("display_name", event.aggregateId())),
                        WorkshopItemState.emptyWorkflow(), null, false, null, 0, event.recordedAt()));
                continue;
            }
            if ("recipe.assigned".equals(event.eventType())) {
                var current = states.get(event.aggregateId());
                if (current == null) continue;
                states.put(current.id(), new WorkshopItemState(
                        current.id(), current.catalogItemId(), current.paintingProjectId(), current.displayName(),
                        current.workflow(), current.currentStage(), current.completed(),
                        text(event.payload().get("recipe_id")), number(event.payload().get("recipe_version")), event.recordedAt()));
                continue;
            }
            if (!event.eventType().startsWith("workflow.stage.")) continue;
            var current = states.get(event.aggregateId());
            if (current == null) continue;
            var stage = WorkflowStage.fromId(text(event.payload().get("stage")));
            var workflow = new EnumMap<>(current.workflow());
            workflow.put(stage, statusFor(event.eventType()));
            var nextStage = first(workflow, WorkflowStageStatus.IN_PROGRESS);
            if (nextStage == null) nextStage = first(workflow, WorkflowStageStatus.PENDING);
            var completed = workflow.values().stream().allMatch(status -> status == WorkflowStageStatus.COMPLETED || status == WorkflowStageStatus.SKIPPED);
            states.put(current.id(), new WorkshopItemState(current.id(), current.catalogItemId(), current.paintingProjectId(), current.displayName(), workflow, nextStage, completed, current.recipeId(), current.recipeVersion(), event.recordedAt()));
        }
        return new ArrayList<>(states.values());
    }

    public static void assertTransition(WorkflowStageStatus current, StageAction action) {
        var allowed = switch (action) {
            case START -> current == WorkflowStageStatus.PENDING;
            case COMPLETE, SKIP -> current == WorkflowStageStatus.PENDING || current == WorkflowStageStatus.IN_PROGRESS;
            case REOPEN -> current == WorkflowStageStatus.COMPLETED || current == WorkflowStageStatus.SKIPPED;
        };
        if (!allowed) throw new DomainException("invalid_transition", "Cannot " + action.id() + " a stage currently marked " + current.id() + ".");
    }

    public static void assertTransition(
            Map<WorkflowStage, WorkflowStageStatus> workflow,
            WorkflowStage stage,
            StageAction action) {
        assertTransition(workflow.get(stage), action);
        if (action == StageAction.REOPEN) return;
        for (var previous : WorkflowStage.values()) {
            if (previous == stage) break;
            var status = workflow.get(previous);
            if (status != WorkflowStageStatus.COMPLETED && status != WorkflowStageStatus.SKIPPED) {
                throw new DomainException("invalid_transition",
                        "Cannot " + action.id() + " stage " + stage.id()
                                + " before stage " + previous.id() + " is completed or skipped.");
            }
        }
    }

    private static WorkflowStage first(Map<WorkflowStage, WorkflowStageStatus> workflow, WorkflowStageStatus status) {
        for (var stage : WorkflowStage.values()) if (workflow.get(stage) == status) return stage;
        return null;
    }

    private static WorkflowStageStatus statusFor(String eventType) {
        if (eventType.endsWith(".started") || eventType.endsWith(".reopened")) return WorkflowStageStatus.IN_PROGRESS;
        if (eventType.endsWith(".completed")) return WorkflowStageStatus.COMPLETED;
        if (eventType.endsWith(".skipped")) return WorkflowStageStatus.SKIPPED;
        throw new DomainException("invalid_event", "Unknown workflow event type: " + eventType);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value)); } catch (NumberFormatException ignored) { return 0; }
    }
}
