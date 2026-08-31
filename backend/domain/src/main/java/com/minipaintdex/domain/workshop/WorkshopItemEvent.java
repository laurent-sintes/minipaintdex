package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;

public sealed interface WorkshopItemEvent extends DomainEvent permits WorkshopItemAdded,
        WorkshopItemCommentAdded, WorkshopItemPhotoAdded, WorkshopItemRecipeAssigned,
        WorkflowStageStarted, WorkflowStageCompleted, WorkflowStageSkipped, WorkflowStageReopened {
    @Override default String aggregateType() { return "workshop_item"; }
}
