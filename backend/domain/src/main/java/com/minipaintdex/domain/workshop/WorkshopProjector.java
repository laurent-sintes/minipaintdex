package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

public final class WorkshopProjector {
    private WorkshopProjector() {}

    public static Workshop project(List<DomainEvent> events) {
        var products = new LinkedHashMap<String, WorkshopProduct>();
        Instant updatedAt = null;
        for (var event : events) {
            String productId = null;
            if ("workshop.product_imported".equals(event.eventType())) {
                productId = text(event.payload().get("product_id"));
            } else if ("project.created".equals(event.eventType())) {
                // Compatibility projection for the ledger written before Product and Workshop existed.
                productId = text(event.payload().get("market_product_id"));
                if (productId.isBlank()) productId = text(event.payload().get("market_game_id"));
                if (productId.isBlank()) productId = event.projectId();
            }
            if (productId != null && !productId.isBlank()) {
                products.putIfAbsent(productId, new WorkshopProduct(productId, event.occurredAt()));
                updatedAt = event.recordedAt();
            }
        }
        return new Workshop(Workshop.DEFAULT_ID, List.copyOf(products.values()), updatedAt);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
