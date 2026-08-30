package com.minipaintdex.application.command;

import java.time.Instant;

public record TransitionStageCommand(
        String itemId,
        String stage,
        String action,
        String comment,
        String reason,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {
}
