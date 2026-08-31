package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopCreated(String workshopId, String name, Instant occurredAt) implements WorkshopEvent {
    public WorkshopCreated {
        workshopId = DomainFields.required(workshopId, "workshopId");
        name = DomainFields.required(name, "name");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "workshop.created"; }
    @Override public String aggregateId() { return workshopId; }
}
