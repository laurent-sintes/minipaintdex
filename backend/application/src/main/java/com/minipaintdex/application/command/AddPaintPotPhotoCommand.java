package com.minipaintdex.application.command;

public record AddPaintPotPhotoCommand(String paintPotId, String originalFilename, String contentType,
        byte[] content, String caption, String actorId, java.time.Instant occurredAt,
        String correlationId, String idempotencyKey) {
    public AddPaintPotPhotoCommand { content = content == null ? new byte[0] : content.clone(); }
    @Override public byte[] content() { return content.clone(); }
}
