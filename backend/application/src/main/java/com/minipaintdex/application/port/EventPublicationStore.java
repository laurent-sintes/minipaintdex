package com.minipaintdex.application.port;

import com.minipaintdex.application.event.EventBatch;
import com.minipaintdex.application.event.EventPublication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Durable state store used by the event dispatcher.
 * State transitions must be atomic and recoverable across process restarts.
 */
public interface EventPublicationStore {
    /** Creates a durable pending record idempotently by batch identity. */
    EventPublication savePending(EventBatch batch);

    /** Atomically claims one delivery attempt. */
    EventPublication markProcessing(String publicationId, Instant at);

    /** Atomically acknowledges successful ledger ingestion. */
    EventPublication markCompleted(String publicationId, Instant at);

    /** Atomically records a failed attempt without discarding its batch. */
    EventPublication markFailed(String publicationId, Instant at, String failure);

    /** Reads one durable publication state. */
    Optional<EventPublication> find(String publicationId);

    /** Lists non-completed publications in deterministic recovery order. */
    List<EventPublication> recoverable();
}
