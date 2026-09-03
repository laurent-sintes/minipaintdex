package com.minipaintdex.application.query;

import com.minipaintdex.domain.market.paint.PaintUsageGuide;
import com.minipaintdex.domain.shared.DomainException;

public record SearchPaintUsageGuidesQuery(String brand, String range, String paintProductId,
        String language, PageQuery page, String correlationId) {
    public SearchPaintUsageGuidesQuery {
        language = PaintUsageGuide.language(language);
        if (page == null || correlationId == null || correlationId.isBlank()) throw new DomainException("invalid_input", "Page and correlationId are required");
    }
}
