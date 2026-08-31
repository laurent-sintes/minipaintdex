package com.minipaintdex.application.port;

import com.minipaintdex.application.document.StructuredDocument;

import java.util.List;

/** Atomic mutation boundary for the market paint reference catalog. */
public interface MarketPaintCatalogWriter {
    /** Replaces the validated catalog as one persistence generation or leaves the old generation intact. */
    void replaceMarketPaints(List<StructuredDocument> paints);

    /** Replaces catalog and inventory as one atomic persistence generation or leaves both unchanged. */
    void replaceMarketPaintsAndWorkshopInventory(
            List<StructuredDocument> paints,
            List<StructuredDocument> inventory,
            WorkshopPaintInventoryWriter inventoryWriter);
}
