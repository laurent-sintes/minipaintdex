package com.minipaintdex.domain.event;

import java.time.Instant;

/** A business fact emitted exclusively by an aggregate root. */
public interface DomainEvent {
    String eventType();

    String aggregateType();

    String aggregateId();

    /** Owning project scope, distinct from a project referenced by a Workshop membership event. */
    String scopePaintingProjectId();

    Instant occurredAt();
}
