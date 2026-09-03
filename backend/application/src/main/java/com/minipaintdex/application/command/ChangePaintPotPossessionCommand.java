package com.minipaintdex.application.command;

public record ChangePaintPotPossessionCommand(String paintPotId, String possession,
        String actorId, java.time.Instant occurredAt, String correlationId, String idempotencyKey) {}
