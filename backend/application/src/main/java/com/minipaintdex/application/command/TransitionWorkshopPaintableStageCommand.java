package com.minipaintdex.application.command;

import java.time.Instant;

public record TransitionWorkshopPaintableStageCommand(
        String workshopPaintableId,
        String stage,
        String action,
        String comment,
        String reason,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {
}
