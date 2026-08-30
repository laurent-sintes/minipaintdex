package com.minipaintdex.application.command;

import java.time.Instant;

public record ImportPaintableProductToWorkshopCommand(
        String productId,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {
}
