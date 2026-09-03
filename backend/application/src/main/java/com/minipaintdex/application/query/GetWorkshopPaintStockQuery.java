package com.minipaintdex.application.query;

import com.minipaintdex.domain.shared.DomainException;

public record GetWorkshopPaintStockQuery(String paintProductId, String correlationId) {
    public GetWorkshopPaintStockQuery {
        if (paintProductId == null || !paintProductId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                || correlationId == null || correlationId.isBlank()) {
            throw new DomainException("invalid_input", "A stable paintProductId and correlationId are required.");
        }
    }
}
