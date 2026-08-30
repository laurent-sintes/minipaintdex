package com.minipaintdex.application.command;

import java.util.List;
import java.util.Map;

public record ApplyMiniatureProjectChangeSetCommand(
        int schemaVersion,
        String kind,
        Map<String, Object> project,
        List<Map<String, Object>> paintingGuides,
        List<WorkshopItem> workshopItems,
        boolean dryRun,
        String actorId,
        String correlationId) {

    public ApplyMiniatureProjectChangeSetCommand {
        project = project == null ? Map.of() : Map.copyOf(project);
        paintingGuides = paintingGuides == null ? List.of() : List.copyOf(paintingGuides);
        workshopItems = workshopItems == null ? List.of() : List.copyOf(workshopItems);
    }

    public record WorkshopItem(String id, String catalogItemId, String projectId, String displayName) {
    }
}
