package com.minipaintdex.application;

import com.minipaintdex.application.port.PaintPotPhotoProcessor;
import com.minipaintdex.application.result.PaintPotPhotoPreview;
import com.minipaintdex.domain.shared.DomainException;
import java.util.Locale;

/** Shared validation and local processing for previews and photo attachment. */
public final class PaintPotPhotoService {
    private final WorkshopMediaPolicy policy;
    private final PaintPotPhotoProcessor processor;

    public PaintPotPhotoService(WorkshopMediaPolicy policy, PaintPotPhotoProcessor processor) {
        this.policy = java.util.Objects.requireNonNull(policy);
        this.processor = java.util.Objects.requireNonNull(processor);
    }

    public PaintPotPhotoPreview preview(String contentType, byte[] content, String correlationId) {
        if (contentType == null || !policy.allowedContentTypes().contains(contentType.toLowerCase(Locale.ROOT)))
            throw new DomainException("invalid_input", "Unsupported pot photo content type.");
        if (content.length == 0 || content.length > policy.maxUploadBytes())
            throw new DomainException("invalid_input", "Pot photo exceeds upload limit.");
        return processor.removeBackground(content, correlationId == null || correlationId.isBlank()
                ? Ulid.next(java.time.Instant.now()) : correlationId);
    }
}
