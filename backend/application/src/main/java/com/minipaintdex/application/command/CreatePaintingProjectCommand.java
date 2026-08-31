package com.minipaintdex.application.command;

import java.time.Instant;

public record CreatePaintingProjectCommand(
        String paintableProductId,
        String paintingProjectId,
        String name,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {
}
