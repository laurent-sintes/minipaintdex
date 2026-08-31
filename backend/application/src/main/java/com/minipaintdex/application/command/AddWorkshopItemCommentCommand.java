package com.minipaintdex.application.command;

import java.time.Instant;

public record AddWorkshopItemCommentCommand(
        String itemId,
        String comment,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {
}
