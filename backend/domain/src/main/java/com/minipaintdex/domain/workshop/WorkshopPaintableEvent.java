package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;

public sealed interface WorkshopPaintableEvent extends DomainEvent permits WorkshopPaintableAdded,
        WorkshopPaintableCommentAdded, WorkshopPaintablePhotoAdded, WorkshopPaintableRecipeAssigned,
        WorkflowStageStarted, WorkflowStageCompleted, WorkflowStageSkipped, WorkflowStageReopened {
    @Override default String aggregateType() { return "workshop_item"; }
}
