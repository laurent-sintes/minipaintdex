package com.minipaintdex.domain.event;

import com.minipaintdex.domain.shared.DomainException;

import java.time.Instant;
import java.util.Objects;

/** Immutable technical envelope around one typed domain event. */
public record EventEnvelope(
        String eventId,
        int schemaVersion,
        long aggregateVersion,
        Instant recordedAt,
        Actor actor,
        String correlationId,
        String causationId,
        String idempotencyKey,
        DomainEvent event) implements DomainEvent {

    public EventEnvelope {
        require(eventId, "event_id");
        if (schemaVersion != 1) throw invalid("schema_version must be 1.");
        if (aggregateVersion < 1) throw invalid("aggregate_version must be positive.");
        recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        actor = Objects.requireNonNull(actor, "actor");
        require(correlationId, "correlation_id");
        event = Objects.requireNonNull(event, "event");
    }

    public String eventType() { return event.eventType(); }
    public String aggregateType() { return event.aggregateType(); }
    public String aggregateId() { return event.aggregateId(); }
    public String projectId() { return event.projectId(); }
    public Instant occurredAt() { return event.occurredAt(); }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " is required.");
    }

    private static DomainException invalid(String message) {
        return new DomainException("invalid_event", message);
    }
}
