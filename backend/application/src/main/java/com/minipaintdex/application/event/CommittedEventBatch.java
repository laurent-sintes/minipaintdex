package com.minipaintdex.application.event;

import java.time.Instant;

/** Notification emitted only after the ledger durably acknowledged an event batch. */
public record CommittedEventBatch(EventBatch batch, Instant committedAt) {
    public CommittedEventBatch {
        if (batch == null) throw new IllegalArgumentException("batch is required");
        committedAt = committedAt == null ? Instant.now() : committedAt;
    }
}
