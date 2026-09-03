package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;

public sealed interface PaintingProjectEvent extends DomainEvent permits PaintingProjectCreated, PaintingProjectStatusChanged {
    @Override default String aggregateType() { return "painting_project"; }
    @Override default String scopePaintingProjectId() { return aggregateId(); }
}
