package com.minipaintdex.bootstrap;

import com.minipaintdex.application.port.PersistenceLifecycle;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

/** Periodically reconciles the in-memory persistence caches with external storage changes. */
final class PersistenceSentinel {
    private final PersistenceLifecycle persistence;
    private final boolean enabled;

    PersistenceSentinel(PersistenceLifecycle persistence, boolean enabled) {
        this.persistence = Objects.requireNonNull(persistence);
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${minipaintdex.storage.sentinel-interval:5s}")
    void check() {
        if (enabled) persistence.refreshIfChanged();
    }
}
