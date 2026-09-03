package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkshopPaintableProjectorTest {
    private static final Instant AT = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    void aggregateOwnsAndProjectsItsWorkflow() {
        var item = WorkshopPaintable.create("ws-1", "soldier", "paint-game", "Soldier", 1, AT);
        item.transition(WorkflowStage.PREPARATION, StageAction.COMPLETE, null, AT.plusSeconds(1));
        item.transition(WorkflowStage.PRIMING, StageAction.START, null, AT.plusSeconds(2));
        var events = item.releaseEvents();

        var state = WorkshopPaintableProjector.project(events).getFirst();
        assertEquals(WorkflowStageStatus.COMPLETED, state.workflow().get(WorkflowStage.PREPARATION));
        assertEquals(WorkflowStageStatus.IN_PROGRESS, state.workflow().get(WorkflowStage.PRIMING));
        assertEquals(WorkflowStage.PRIMING, state.currentStage());
    }

    @Test
    void aggregateRejectsAStageBeforeItsPrerequisites() {
        var item = WorkshopPaintable.create("ws-1", "soldier", "paint-game", "Soldier", 1, AT);
        assertThrows(DomainException.class, () ->
                item.transition(WorkflowStage.PAINTING, StageAction.COMPLETE, null, AT));
    }

    @Test
    void skipRequiresAnExplicitReason() {
        var item = WorkshopPaintable.create("ws-1", "soldier", "paint-game", "Soldier", 1, AT);
        assertThrows(DomainException.class, () ->
                item.transition(WorkflowStage.PREPARATION, StageAction.SKIP, null, AT));
    }
}
