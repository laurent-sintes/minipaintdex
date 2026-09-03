package com.minipaintdex.application.port;

import java.time.Instant;

/** Infrastructure lifecycle used to initialize, monitor and refresh persistence-backed state. */
public interface PersistenceLifecycle {
    /** Validates all mandatory storage and atomically publishes the first readable generation. */
    InitializationReport initialize();

    /** Refreshes only after a valid external change; otherwise retains the last valid generation. */
    RefreshResult refreshIfChanged();

    /** Returns the latest lock-free operational status and synchronization metadata. */
    PersistenceStatus status();

    record InitializationReport(PersistenceStatus status, int paintProductCount, int paintableProductCount, int eventCount) {}

    record RefreshResult(boolean changed, PersistenceStatus status) {}

    record PersistenceStatus(
            String state,
            String storage,
            long generation,
            String fingerprint,
            Instant initializedAt,
            Instant lastCheckedAt,
            Instant lastSynchronizedAt,
            String detail) {
        public boolean ready() {
            return "ready".equals(state);
        }
    }
}
