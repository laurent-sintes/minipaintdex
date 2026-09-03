package com.minipaintdex.application.query;

public record PreviewPaintPotPhotoQuery(String paintPotId, String contentType, byte[] content, String correlationId) {
    public PreviewPaintPotPhotoQuery { content = content == null ? new byte[0] : content.clone(); }
    @Override public byte[] content() { return content.clone(); }
}
