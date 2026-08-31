package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventSourcedAggregateRoot;
import com.minipaintdex.domain.shared.DomainException;

import java.time.Instant;
import java.util.List;

/** Aggregate root for the owner's purchase decision concerning one shopping-list item. */
public final class ShoppingItem extends EventSourcedAggregateRoot {
    private final String id;
    private boolean checked;
    private Instant updatedAt;

    private ShoppingItem(String id, boolean checked) {
        this.id = DomainFields.id(id, "shoppingItemId");
        this.checked = checked;
    }

    public static ShoppingItem current(String id, boolean checked, List<? extends ShoppingItemEvent> history) {
        var item = new ShoppingItem(id, checked);
        history.forEach(item::replay);
        return item;
    }

    public void setChecked(boolean target, Instant occurredAt) {
        if (checked == target) return;
        raise(new ShoppingItemStatusChanged(id, target, occurredAt));
    }

    @Override
    protected void apply(DomainEvent event) {
        if (event instanceof ShoppingItemStatusChanged changed) {
            checked = changed.checked();
            updatedAt = changed.occurredAt();
            return;
        }
        throw new DomainException("invalid_shopping_item_event",
                "Unsupported shopping item event: " + event.eventType());
    }

    @Override public String id() { return id; }
    public boolean checked() { return checked; }
    public Instant updatedAt() { return updatedAt; }
}
