package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;

public sealed interface WorkshopEvent extends DomainEvent permits WorkshopCreated, PaintingProjectRegistered {
    @Override default String aggregateType() { return "workshop"; }
    @Override default String projectId() { return null; }
}
