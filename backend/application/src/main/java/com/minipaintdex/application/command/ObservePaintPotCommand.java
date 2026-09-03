package com.minipaintdex.application.command;

public record ObservePaintPotCommand(String paintPotId, String condition, String remainingLevel,
        String actorId, java.time.Instant occurredAt, String correlationId, String idempotencyKey) {}
