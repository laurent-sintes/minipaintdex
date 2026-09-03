package com.minipaintdex.application.command;

import java.time.Instant;

public record AddWorkshopPaintableCommand(
        String workshopPaintableId,
        String paintableComponentId,
        String paintingProjectId,
        String displayName,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {
}
