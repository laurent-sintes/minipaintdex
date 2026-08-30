package com.minipaintdex.application.command;

import java.util.List;
import java.util.Map;

public record ApplyMarketPaintableProductChangeSetCommand(
        int schemaVersion,
        String kind,
        Map<String, Object> product,
        List<Map<String, Object>> paintingGuides,
        boolean dryRun,
        String actorId,
        String correlationId) {

    public ApplyMarketPaintableProductChangeSetCommand {
        product = product == null ? Map.of() : Map.copyOf(product);
        paintingGuides = paintingGuides == null ? List.of() : List.copyOf(paintingGuides);
    }
}
