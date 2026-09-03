package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventSourcedAggregateRoot;
import com.minipaintdex.domain.shared.DomainException;

import java.time.Instant;
import java.util.List;

/** Owns the checked marker of a shopping-list entry; checking does not purchase or add stock. */
public final class ShoppingListEntry extends EventSourcedAggregateRoot {
    private final String id;
    private boolean checked;
    private Instant updatedAt;

    private ShoppingListEntry(String id, boolean checked) {
        this.id = DomainFields.id(id, "shoppingListEntryId");
        this.checked = checked;
    }

    public static ShoppingListEntry current(String id, boolean checked, List<? extends ShoppingListEntryEvent> history) {
        var item = new ShoppingListEntry(id, checked);
        history.forEach(item::replay);
        return item;
    }

    public void setChecked(boolean target, Instant occurredAt) {
        if (checked == target) return;
        raise(new ShoppingListEntryCheckedChanged(id, target, occurredAt));
    }

    @Override
    protected void apply(DomainEvent event) {
        if (event instanceof ShoppingListEntryCheckedChanged changed) {
            checked = changed.checked();
            updatedAt = changed.occurredAt();
            return;
        }
        throw new DomainException("invalid_shopping_item_event",
                "Unsupported shopping list entry event: " + event.eventType());
    }

    @Override public String id() { return id; }
    public boolean checked() { return checked; }
    public Instant updatedAt() { return updatedAt; }
}
