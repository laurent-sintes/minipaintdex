package com.minipaintdex.application.port;

import com.minipaintdex.domain.event.DomainEvent;

import java.util.List;

public interface EventLedger {
    default DomainEvent append(DomainEvent event) {
        return appendAll(List.of(event)).getFirst();
    }

    /**
     * Appends one atomic batch. Implementations must enforce idempotency inside the same
     * critical section as the write and return the already persisted events when the
     * complete batch has already been recorded.
     */
    List<DomainEvent> appendAll(List<DomainEvent> events);
}
