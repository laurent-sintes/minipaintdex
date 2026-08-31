package com.minipaintdex.application.command;

import com.minipaintdex.application.document.StructuredDocument;

import java.util.List;

public record ApplyMarketPaintableProductChangeSetCommand(
        int schemaVersion,
        String kind,
        StructuredDocument product,
        List<StructuredDocument> paintingGuides,
        boolean dryRun,
        String actorId,
        String correlationId) {

    public ApplyMarketPaintableProductChangeSetCommand {
        product = product == null ? new StructuredDocument(List.of()) : product;
        paintingGuides = paintingGuides == null ? List.of() : List.copyOf(paintingGuides);
    }
}
