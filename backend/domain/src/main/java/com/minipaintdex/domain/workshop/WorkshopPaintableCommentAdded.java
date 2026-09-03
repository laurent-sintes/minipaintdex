package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopPaintableCommentAdded(
        String workshopPaintableId, String paintingProjectId, String comment, Instant occurredAt) implements WorkshopPaintableEvent {
    public WorkshopPaintableCommentAdded {
        workshopPaintableId = DomainFields.id(workshopPaintableId, "workshopPaintableId");
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        comment = DomainFields.required(comment, "comment");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "workshop_item.comment_added"; }
    @Override public String aggregateId() { return workshopPaintableId; }
    @Override public String scopePaintingProjectId() { return paintingProjectId; }
}
