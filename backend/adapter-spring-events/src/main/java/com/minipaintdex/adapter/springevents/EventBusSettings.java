package com.minipaintdex.adapter.springevents;

import java.time.Duration;

public record EventBusSettings(
        int workerCount,
        int queueCapacity,
        int maxAttempts,
        Duration retryDelay,
        Duration shutdownTimeout) {
    public EventBusSettings {
        if (workerCount != 1) throw new IllegalArgumentException("The global file ledger requires exactly one worker");
        if (queueCapacity < 1) throw new IllegalArgumentException("queueCapacity must be positive");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        if (retryDelay == null || retryDelay.isNegative()) throw new IllegalArgumentException("retryDelay is invalid");
        if (shutdownTimeout == null || shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException("shutdownTimeout must be positive");
        }
    }
}
