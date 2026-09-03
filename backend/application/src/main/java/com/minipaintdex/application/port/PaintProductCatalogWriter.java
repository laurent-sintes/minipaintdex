package com.minipaintdex.application.port;

import com.minipaintdex.application.document.StructuredDocument;

import java.util.List;

/** Atomic mutation boundary for the market paint reference catalog. */
public interface PaintProductCatalogWriter {
    /**
     * Atomically replaces validated paint, edition and usage-guide documents as one generation. Ordering is
     * canonicalized by identity; replaying identical contents is idempotent. Implementations share
     * the storage write lock, preserve the previous generation on failure, publish reads only after
     * durable replacement and retain no caller-owned mutable resources. No workshop data is changed.
     */
    void replacePaintProductCatalog(List<StructuredDocument> paints, List<StructuredDocument> editions, List<StructuredDocument> usageGuides);

    /** Replaces the validated catalog as one persistence generation or leaves the old generation intact. */
    void replacePaintProducts(List<StructuredDocument> paints);

    /**
     * Rekeys paints and every mutable file-backed reference as one persistence generation.
     * Implementations must leave the previous generation intact when any replacement fails.
     */
    void replacePaintProductIdentities(
            List<StructuredDocument> paints,
            List<StructuredDocument> paintingGuides,
            List<StructuredDocument> shopping);
}
