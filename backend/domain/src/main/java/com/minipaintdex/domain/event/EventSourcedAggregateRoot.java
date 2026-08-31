package com.minipaintdex.domain.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared mechanics for aggregate-local decision and event application. */
public abstract class EventSourcedAggregateRoot implements AggregateRoot {
    private final List<DomainEvent> pendingEvents = new ArrayList<>();
    private long version;

    protected final void raise(DomainEvent event) {
        apply(Objects.requireNonNull(event));
        pendingEvents.add(event);
        version++;
    }

    protected final void replay(DomainEvent event) {
        apply(Objects.requireNonNull(event));
        version++;
    }

    protected abstract void apply(DomainEvent event);

    @Override
    public final long version() {
        return version;
    }

    @Override
    public final List<DomainEvent> pendingEvents() {
        return List.copyOf(pendingEvents);
    }

    @Override
    public final List<DomainEvent> releaseEvents() {
        var result = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return result;
    }
}
