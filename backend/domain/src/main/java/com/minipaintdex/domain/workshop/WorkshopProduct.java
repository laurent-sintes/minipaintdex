package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.workflow.DomainException;

import java.time.Instant;
import java.util.Objects;

/** Membership of one market product in a workshop. Physical copies remain WorkshopItem aggregates. */
public record WorkshopProduct(String productId, Instant importedAt) {
    public WorkshopProduct {
        if (productId == null || productId.isBlank()) {
            throw new DomainException("invalid_workshop", "Workshop product id is required.");
        }
        importedAt = Objects.requireNonNull(importedAt, "importedAt");
    }
}
