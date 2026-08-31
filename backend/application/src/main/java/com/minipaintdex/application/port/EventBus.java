package com.minipaintdex.application.port;

import com.minipaintdex.application.event.EventBatch;
import com.minipaintdex.application.event.EventPublication;
import com.minipaintdex.application.event.EventBusState;
import com.minipaintdex.application.event.PublicationReceipt;

import java.time.Duration;
import java.util.Optional;

/**
 * Durable publication boundary for domain-event batches.
 * Implementations must persist acceptance before returning and preserve event order inside a batch.
 */
public interface EventBus {
    /** Persists the batch as pending before returning and rejects new work after admission closes. */
    PublicationReceipt publish(EventBatch batch);

    /** Reads the durable publication state when it exists. */
    Optional<EventPublication> publication(String publicationId);

    /** Waits for commit or terminal failure without changing publication state. */
    EventPublication await(String publicationId, Duration timeout) throws InterruptedException;

    /** Returns a non-blocking operational snapshot for health and administration adapters. */
    EventBusState state();
}
