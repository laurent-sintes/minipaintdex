package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopPaintableAdded(
        String workshopPaintableId, String paintableComponentId, String paintingProjectId,
        String displayName, int ordinal, Instant occurredAt) implements WorkshopPaintableEvent {
    public WorkshopPaintableAdded {
        workshopPaintableId = DomainFields.id(workshopPaintableId, "workshopPaintableId");
        paintableComponentId = DomainFields.id(paintableComponentId, "paintableComponentId");
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        displayName = DomainFields.required(displayName, "displayName");
        if (ordinal < 1) throw DomainFields.invalid("ordinal must be positive.");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "workshop_item.added"; }
    @Override public String aggregateId() { return workshopPaintableId; }
    @Override public String scopePaintingProjectId() { return paintingProjectId; }
}
