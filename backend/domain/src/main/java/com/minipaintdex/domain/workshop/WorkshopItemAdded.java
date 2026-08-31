package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopItemAdded(
        String workshopItemId, String catalogItemId, String paintingProjectId,
        String displayName, int ordinal, Instant occurredAt) implements WorkshopItemEvent {
    public WorkshopItemAdded {
        workshopItemId = DomainFields.required(workshopItemId, "workshopItemId");
        catalogItemId = DomainFields.required(catalogItemId, "catalogItemId");
        paintingProjectId = DomainFields.required(paintingProjectId, "paintingProjectId");
        displayName = DomainFields.required(displayName, "displayName");
        if (ordinal < 0) throw DomainFields.invalid("ordinal cannot be negative.");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "workshop_item.added"; }
    @Override public String aggregateId() { return workshopItemId; }
    @Override public String projectId() { return paintingProjectId; }
}
