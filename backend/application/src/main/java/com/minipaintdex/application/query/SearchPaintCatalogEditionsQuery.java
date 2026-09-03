package com.minipaintdex.application.query;

import com.minipaintdex.domain.shared.DomainException;

public record SearchPaintCatalogEditionsQuery(String brand, PageQuery page, String correlationId) {
    public SearchPaintCatalogEditionsQuery {
        if (page == null) throw new DomainException("invalid_input", "page is required");
        if (correlationId == null || correlationId.isBlank()) throw new DomainException("invalid_input", "correlationId is required");
    }
}
