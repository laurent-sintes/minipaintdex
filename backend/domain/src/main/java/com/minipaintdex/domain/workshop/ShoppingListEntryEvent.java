package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;

public sealed interface ShoppingListEntryEvent extends DomainEvent permits ShoppingListEntryCheckedChanged {
    @Override default String aggregateType() { return "shopping_item"; }
    @Override default String scopePaintingProjectId() { return null; }
}
