package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.workflow.StageAction;
import com.minipaintdex.domain.workflow.WorkflowStage;
import com.minipaintdex.domain.workflow.WorkflowStageStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkshopItemProjectorTest {
    @Test
    void projectsOnePhysicalItemAndItsOwnProgress() {
        var now = Instant.parse("2026-08-30T12:00:00Z");
        var actor = new Actor("user", "owner");
        var added = new DomainEvent("01", 1, "workshop_item.added", now, now, "workshop_item", "ws-1", "paint-reichbusters", actor, "c1", null, null, Map.of("catalog_item_id", "soldier", "painting_project_id", "paint-reichbusters", "display_name", "Soldier 1"));
        var primed = new DomainEvent("02", 1, "workflow.stage.completed", now, now, "workshop_item", "ws-1", "reichbusters-reloaded", actor, "c2", "01", null, Map.of("stage", "priming"));

        var item = WorkshopItemProjector.project(List.of(added, primed)).getFirst();

        assertEquals("soldier", item.catalogItemId());
        assertEquals(WorkflowStageStatus.COMPLETED, item.workflow().get(WorkflowStage.PRIMING));
        assertEquals(WorkflowStage.PREPARATION, item.currentStage());
    }

    @Test
    void rejectsCompletingAStageBeforeItsPrerequisites() {
        var workflow = WorkshopItemState.emptyWorkflow();

        assertThrows(RuntimeException.class,
                () -> WorkshopItemProjector.assertTransition(workflow, WorkflowStage.PAINTING, StageAction.COMPLETE));
    }

    @Test
    void acceptsTheNextStageWhenPreviousStagesAreDone() {
        var workflow = new java.util.EnumMap<>(WorkshopItemState.emptyWorkflow());
        workflow.put(WorkflowStage.PREPARATION, WorkflowStageStatus.COMPLETED);

        WorkshopItemProjector.assertTransition(workflow, WorkflowStage.PRIMING, StageAction.START);
    }
}
