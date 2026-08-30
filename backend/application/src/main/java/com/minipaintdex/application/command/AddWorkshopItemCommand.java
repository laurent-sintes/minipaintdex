package com.minipaintdex.application.command;

import java.time.Instant;

public record AddWorkshopItemCommand(
        String itemId,
        String catalogItemId,
        String workshopProductId,
        String displayName,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {
}
