package com.minipaintdex.application.command;

import java.time.Instant;

public record SetShoppingListEntryCheckedCommand(
        String shoppingListEntryId,
        boolean checked,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {
}
