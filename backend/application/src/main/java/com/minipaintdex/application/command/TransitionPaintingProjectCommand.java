package com.minipaintdex.application.command;

import java.time.Instant;

/** Intent to change the lifecycle of one painting-project aggregate. */
public record TransitionPaintingProjectCommand(
        String paintingProjectId,
        String targetStatus,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {
}
