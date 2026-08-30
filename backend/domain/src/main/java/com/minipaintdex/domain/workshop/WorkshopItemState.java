package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.workflow.WorkflowStage;
import com.minipaintdex.domain.workflow.WorkflowStageStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record WorkshopItemState(
        String id,
        String catalogItemId,
        String projectId,
        String displayName,
        Map<WorkflowStage, WorkflowStageStatus> workflow,
        WorkflowStage currentStage,
        boolean completed,
        Instant updatedAt) {

    public WorkshopItemState {
        workflow = Collections.unmodifiableMap(new EnumMap<>(workflow));
    }

    public static Map<WorkflowStage, WorkflowStageStatus> emptyWorkflow() {
        var result = new EnumMap<WorkflowStage, WorkflowStageStatus>(WorkflowStage.class);
        for (var stage : WorkflowStage.values()) result.put(stage, WorkflowStageStatus.PENDING);
        return result;
    }
}
