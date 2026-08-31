package com.minipaintdex.application.view;

import java.time.Instant;

/** Outcome of a deterministic projection rebuild from the authoritative ledger. */
public record RebuildProjectionResult(
        String status,
        String storage,
        int eventCount,
        Instant rebuiltAt,
        int paints,
        int marketPaintableProducts,
        int paintingProjects,
        int workshopItems,
        int marketPaintingGuides,
        int workshopRecipes) {}
