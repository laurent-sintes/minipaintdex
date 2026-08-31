package com.minipaintdex.application.port;

/**
 * Reads one coherent Market reference generation.
 * Implementations must never enrich it with ownership, projects, progress or other Workshop state.
 */
@FunctionalInterface
public interface MarketCatalogReader {
    MarketCatalogSnapshot load();
}
