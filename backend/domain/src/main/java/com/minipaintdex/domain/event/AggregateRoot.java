package com.minipaintdex.domain.event;

import java.util.List;

/** Contract implemented by event-sourced aggregate roots. */
public interface AggregateRoot {
    /** Stable domain identity, available after creation or non-empty rehydration. */
    String id();

    /** Last applied aggregate version, including newly raised events. */
    long version();

    /** Immutable view of events raised since rehydration. */
    List<DomainEvent> pendingEvents();

    /** Atomically returns and clears pending events while retaining aggregate state. */
    List<DomainEvent> releaseEvents();
}
