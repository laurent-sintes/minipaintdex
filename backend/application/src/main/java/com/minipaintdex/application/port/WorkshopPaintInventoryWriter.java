package com.minipaintdex.application.port;

import com.minipaintdex.application.document.StructuredDocument;

import java.util.List;

/** Atomic mutation boundary for the owner's paint identifiers and quantities. */
public interface WorkshopPaintInventoryWriter {
    /** Replaces the complete validated inventory or preserves the preceding generation on failure. */
    void replaceWorkshopPaints(List<StructuredDocument> paints);
}
