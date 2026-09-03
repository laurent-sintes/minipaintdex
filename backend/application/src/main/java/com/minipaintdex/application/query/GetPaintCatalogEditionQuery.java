package com.minipaintdex.application.query;

import com.minipaintdex.domain.shared.DomainException;

public record GetPaintCatalogEditionQuery(String id, String correlationId) {
    public GetPaintCatalogEditionQuery {
        if (id == null || !id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw new DomainException("invalid_input", "Invalid edition id");
        if (correlationId == null || correlationId.isBlank()) throw new DomainException("invalid_input", "correlationId is required");
    }
}
