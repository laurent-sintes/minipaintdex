package com.minipaintdex.application.port;

import com.minipaintdex.application.document.StructuredDocument;

import java.util.List;

/** Atomic mutation boundary for the market paint reference catalog. */
public interface MarketPaintCatalogWriter {
    /**
     * Atomically replaces validated paint and edition documents as one generation. Ordering is
     * canonicalized by identity; replaying identical contents is idempotent. Implementations share
     * the storage write lock, preserve the previous generation on failure, publish reads only after
     * durable replacement and retain no caller-owned mutable resources. No workshop data is changed.
     */
    void replaceMarketPaintCatalog(List<StructuredDocument> paints, List<StructuredDocument> editions);

    /** Replaces the validated catalog as one persistence generation or leaves the old generation intact. */
    void replaceMarketPaints(List<StructuredDocument> paints);

    /** Replaces catalog and inventory as one atomic persistence generation or leaves both unchanged. */
    void replaceMarketPaintsAndWorkshopInventory(
            List<StructuredDocument> paints,
            List<StructuredDocument> inventory,
            WorkshopPaintInventoryWriter inventoryWriter);

    /**
     * Rekeys paints and every mutable file-backed reference as one persistence generation.
     * Implementations must leave the previous generation intact when any replacement fails.
     */
    void replaceMarketPaintIdentities(
            List<StructuredDocument> paints,
            List<StructuredDocument> inventory,
            List<StructuredDocument> paintingGuides,
            List<StructuredDocument> shopping);
}
