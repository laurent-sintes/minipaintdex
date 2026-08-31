package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;

public sealed interface ShoppingItemEvent extends DomainEvent permits ShoppingItemStatusChanged {
    @Override default String aggregateType() { return "shopping_item"; }
    @Override default String projectId() { return null; }
}
