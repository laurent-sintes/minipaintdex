package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopItemCommentAdded(
        String workshopItemId, String paintingProjectId, String comment, Instant occurredAt) implements WorkshopItemEvent {
    public WorkshopItemCommentAdded {
        workshopItemId = DomainFields.required(workshopItemId, "workshopItemId");
        paintingProjectId = DomainFields.required(paintingProjectId, "paintingProjectId");
        comment = DomainFields.required(comment, "comment");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "workshop_item.comment_added"; }
    @Override public String aggregateId() { return workshopItemId; }
    @Override public String projectId() { return paintingProjectId; }
}
