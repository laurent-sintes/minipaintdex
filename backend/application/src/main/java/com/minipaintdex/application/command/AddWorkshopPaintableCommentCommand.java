package com.minipaintdex.application.command;

import java.time.Instant;

public record AddWorkshopPaintableCommentCommand(
        String workshopPaintableId,
        String comment,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {
}
