package com.minipaintdex.domain.event;

import java.time.Instant;

/** A business fact emitted exclusively by an aggregate root. */
public interface DomainEvent {
    String eventType();

    String aggregateType();

    String aggregateId();

    String projectId();

    Instant occurredAt();
}
