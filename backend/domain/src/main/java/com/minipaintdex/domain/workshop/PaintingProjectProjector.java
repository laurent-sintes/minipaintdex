package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;

import java.util.LinkedHashMap;
import java.util.List;

/** Projects the current painting-project aggregates from the application ledger. */
public final class PaintingProjectProjector {
    private PaintingProjectProjector() {}

    public static List<PaintingProject> project(List<DomainEvent> events) {
        var projectsByProduct = new LinkedHashMap<String, PaintingProject>();
        for (var event : events) {
            if ("painting_project.created".equals(event.eventType())) {
                var productId = text(event.payload().get("paintable_product_id"));
                if (productId.isBlank()) productId = text(event.payload().get("product_id"));
                if (productId.isBlank()) continue;
                var name = text(event.payload().get("name"));
                if (name.isBlank()) name = productId;
                projectsByProduct.put(productId, new PaintingProject(
                        event.aggregateId(), productId, name, PaintingProjectStatus.ACTIVE,
                        event.occurredAt(), event.recordedAt()));
                continue;
            }
        }
        return List.copyOf(projectsByProduct.values());
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
