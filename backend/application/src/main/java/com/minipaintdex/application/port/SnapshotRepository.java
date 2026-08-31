package com.minipaintdex.application.port;

/** Consistent read boundary; each load returns one immutable persistence generation. */
public interface SnapshotRepository {
    /** Returns the latest valid generation and never exposes a partially refreshed state. */
    DataSnapshot load();
}
