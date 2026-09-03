package com.minipaintdex.application.command;

import java.time.Instant;

public record AddWorkshopPaintablePhotoCommand(
        String workshopPaintableId,
        String originalFilename,
        String contentType,
        byte[] content,
        String stage,
        String caption,
        String actorId,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey) {

    public AddWorkshopPaintablePhotoCommand {
        content = content == null ? new byte[0] : content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
