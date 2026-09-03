package com.minipaintdex.application.port;

import com.minipaintdex.application.result.PaintPotPhotoPreview;

/**
 * Local image segmentation boundary. Accepts validated upload bytes, verifies readable image
 * content and pixel limits before decoding, and returns a transparent PNG without persistence
 * or network calls. The implementation bounds concurrent work and native resources; overload
 * or disabled processing raises photo_processing_unavailable, invalid images raise invalid_input.
 * Calls do not emit events, require idempotency keys, or modify the supplied bytes. Returned
 * content is caller-owned and repeat calls use the same pinned model and processing method.
 * Bootstrap owns the processor lifetime and closes native resources after in-flight HTTP work.
 */
public interface PaintPotPhotoProcessor {
    PaintPotPhotoPreview removeBackground(byte[] content, String correlationId);
}
