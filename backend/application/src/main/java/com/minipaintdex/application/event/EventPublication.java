package com.minipaintdex.application.event;

import java.time.Instant;

/** Durable state of one asynchronous publication. */
public record EventPublication(
        String publicationId,
        EventPublicationStatus status,
        EventBatch batch,
        Instant createdAt,
        Instant updatedAt,
        int attempts,
        String failure) {
    public EventPublication {
        if (publicationId == null || publicationId.isBlank()) {
            throw new IllegalArgumentException("publicationId is required");
        }
        if (status == null || batch == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Publication state is incomplete");
        }
        if (attempts < 0) throw new IllegalArgumentException("attempts cannot be negative");
    }
}
