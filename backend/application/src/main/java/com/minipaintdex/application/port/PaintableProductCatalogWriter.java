package com.minipaintdex.application.port;

import com.minipaintdex.application.document.StructuredDocument;

import java.util.List;

/** Atomic mutation boundary for one market paintable product and its guides. */
public interface PaintableProductCatalogWriter {
    /** Replaces product and guide documents together; partial replacement is forbidden. */
    void replaceProduct(
            String paintableProductId,
            StructuredDocument product,
            List<StructuredDocument> paintingGuides);
}
