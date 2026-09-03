package com.minipaintdex.application.command;

public record OpenPaintPotCommand(String paintPotId,
        String actorId, java.time.Instant occurredAt, String correlationId, String idempotencyKey) {}
