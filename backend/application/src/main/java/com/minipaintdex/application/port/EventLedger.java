package com.minipaintdex.application.port;

import com.minipaintdex.application.event.EventBatch;
import com.minipaintdex.domain.event.EventEnvelope;

import java.util.List;

/** Authoritative append-only workshop journal and the initial critical EventBus subscriber. */
public interface EventLedger extends EventSubscriber {
    /** Appends one event through the same atomic and idempotent path as a batch. */
    default EventEnvelope append(EventEnvelope event) {
        return appendAll(List.of(event)).getFirst();
    }

    /**
     * Appends one atomic batch. Implementations must enforce idempotency inside the same
     * critical section as the write and return the already persisted events when the
     * complete batch has already been recorded.
     */
    List<EventEnvelope> appendAll(List<EventEnvelope> events);

    @Override
    default void consume(EventBatch batch) {
        appendAll(batch.events());
    }
}
