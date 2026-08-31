package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record ShoppingItemStatusChanged(
        String shoppingItemId, boolean checked, Instant occurredAt) implements ShoppingItemEvent {
    public ShoppingItemStatusChanged {
        shoppingItemId = DomainFields.id(shoppingItemId, "shoppingItemId");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "shopping_item.status_changed"; }
    @Override public String aggregateId() { return shoppingItemId; }
}
