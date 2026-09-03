package com.minipaintdex.application.query;

import com.minipaintdex.domain.market.paint.PaintUsageGuide;
import com.minipaintdex.domain.shared.DomainException;

public record GetPaintUsageGuideQuery(String paintUsageGuideId, String language, String correlationId) {
    public GetPaintUsageGuideQuery {
        language = PaintUsageGuide.language(language);
        if (paintUsageGuideId == null || paintUsageGuideId.isBlank() || correlationId == null || correlationId.isBlank()) {
            throw new DomainException("invalid_input", "Guide ID and correlationId are required");
        }
    }
}
