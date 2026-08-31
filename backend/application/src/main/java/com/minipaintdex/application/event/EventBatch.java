package com.minipaintdex.application.event;

import com.minipaintdex.domain.event.EventEnvelope;

import java.time.Instant;
import java.util.List;

/** One atomic, ordered set of event envelopes produced by a single application command. */
public record EventBatch(
        String batchId,
        String correlationId,
        String idempotencyKey,
        Instant acceptedAt,
        List<EventEnvelope> events) {
    public EventBatch {
        if (batchId == null || batchId.isBlank()) throw new IllegalArgumentException("batchId is required");
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("correlationId is required");
        acceptedAt = acceptedAt == null ? Instant.now() : acceptedAt;
        events = events == null ? List.of() : List.copyOf(events);
        if (events.isEmpty()) throw new IllegalArgumentException("An event batch cannot be empty");
    }
}
