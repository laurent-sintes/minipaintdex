package com.minipaintdex.application.port;

/** Small cache abstraction for immutable, generation-tagged application snapshots. */
public interface VersionedCache<T> {
    /** Returns the current immutable generation or an explicitly empty initial value. */
    VersionedValue<T> current();

    /** Atomically replaces the visible value when the supplied generation is newer. */
    void publish(long generation, T value);

    /** Marks the value unavailable without mutating previously returned immutable values. */
    void invalidate();

    record VersionedValue<T>(long generation, T value) {}
}
