package com.minipaintdex.domain.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.minipaintdex.domain.shared.DomainException;

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

    /** Validates aggregate identity and creation ordering before replaying a durable history. */
    protected final void replayHistory(
            List<? extends DomainEvent> history,
            Class<? extends DomainEvent> creationType,
            String aggregateType) {
        if (history == null || history.isEmpty()) {
            throw invalidHistory("Aggregate history must start with a creation event.");
        }
        var first = Objects.requireNonNull(history.getFirst(), "History event is required.");
        if (!creationType.isInstance(first)) {
            throw invalidHistory("First event must be " + creationType.getSimpleName() + ".");
        }
        var aggregateId = first.aggregateId();
        var projectId = first.projectId();
        for (var index = 0; index < history.size(); index++) {
            var event = Objects.requireNonNull(history.get(index), "History event is required.");
            if (index > 0 && creationType.isInstance(event)) {
                throw invalidHistory("Creation event cannot occur more than once.");
            }
            if (!aggregateType.equals(event.aggregateType())) {
                throw invalidHistory("Unexpected aggregate type " + event.aggregateType() + ".");
            }
            if (!aggregateId.equals(event.aggregateId())) {
                throw invalidHistory("Aggregate identity changed from " + aggregateId
                        + " to " + event.aggregateId() + ".");
            }
            if (!Objects.equals(projectId, event.projectId())) {
                throw invalidHistory("Project identity changed in aggregate " + aggregateId + ".");
            }
            replay(event);
        }
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

    private static DomainException invalidHistory(String message) {
        return new DomainException("invalid_aggregate_history", message);
    }
}
