package com.minipaintdex.domain.event;

import java.time.Instant;
import java.util.Map;

public record DomainEvent(
        String eventId,
        int schemaVersion,
        String eventType,
        Instant occurredAt,
        Instant recordedAt,
        String aggregateType,
        String aggregateId,
        String projectId,
        Actor actor,
        String correlationId,
        String causationId,
        String idempotencyKey,
        Map<String, Object> payload) {

    public DomainEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
