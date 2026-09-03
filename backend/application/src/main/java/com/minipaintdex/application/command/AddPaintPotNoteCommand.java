package com.minipaintdex.application.command;

public record AddPaintPotNoteCommand(String paintPotId, String note,
        String actorId, java.time.Instant occurredAt, String correlationId, String idempotencyKey) {}
