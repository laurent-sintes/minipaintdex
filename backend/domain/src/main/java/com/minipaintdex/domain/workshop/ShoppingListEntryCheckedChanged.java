package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record ShoppingListEntryCheckedChanged(
        String shoppingListEntryId, boolean checked, Instant occurredAt) implements ShoppingListEntryEvent {
    public ShoppingListEntryCheckedChanged {
        shoppingListEntryId = DomainFields.id(shoppingListEntryId, "shoppingListEntryId");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "shopping_item.status_changed"; }
    @Override public String aggregateId() { return shoppingListEntryId; }
}
