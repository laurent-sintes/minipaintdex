package com.minipaintdex.application.command;

public record RegisterPaintPotCommand(String paintPotId, String paintProductId, java.time.Instant acquiredAt,
        String actorId, String correlationId, String idempotencyKey) {}
