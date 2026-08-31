package com.minipaintdex.application.command;

import java.time.Instant;

public record SetShoppingItemStatusCommand(
        String itemId,
        boolean checked,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {
}
