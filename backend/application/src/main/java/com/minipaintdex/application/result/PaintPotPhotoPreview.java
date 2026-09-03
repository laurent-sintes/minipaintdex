package com.minipaintdex.application.result;

/** Transient PNG preview; never a stored media identity or an accepted domain command. */
public record PaintPotPhotoPreview(byte[] content, String processingMethod, String correlationId) {
    public PaintPotPhotoPreview { content = content.clone(); }
    @Override public byte[] content() { return content.clone(); }
}
