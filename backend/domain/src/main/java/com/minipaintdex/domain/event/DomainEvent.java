package com.minipaintdex.domain.event;

import com.minipaintdex.domain.workflow.DomainException;

import java.time.Instant;
import java.util.Map;

public record DomainEvent(
        String eventId,
        int schemaVersion,
        String eventType,
        Instant occurredAt,
        Instant recordedAt,
        String aggregateType,
        String aggregateId,
        String projectId,
        Actor actor,
        String correlationId,
        String causationId,
        String idempotencyKey,
        Map<String, Object> payload) {

    public DomainEvent {
        require(eventId, "event_id");
        if (schemaVersion < 1) throw new DomainException("invalid_event", "schema_version must be positive");
        require(eventType, "event_type");
        if (!eventType.matches("[a-z0-9_]+(?:\\.[a-z0-9_]+)+")) {
            throw new DomainException("invalid_event", "event_type must use lowercase dotted identifiers");
        }
        if (occurredAt == null) throw new DomainException("invalid_event", "occurred_at is required");
        if (recordedAt == null) throw new DomainException("invalid_event", "recorded_at is required");
        require(aggregateType, "aggregate_type");
        require(aggregateId, "aggregate_id");
        if (actor == null) throw new DomainException("invalid_event", "actor is required");
        require(correlationId, "correlation_id");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        if ("painting_project.created".equals(eventType)) {
            require(payload.get("workshop_id"), "payload.workshop_id");
            require(payload.get("paintable_product_id"), "payload.paintable_product_id");
            require(payload.get("name"), "payload.name");
        }
        if ("workshop_item.added".equals(eventType)) {
            require(payload.get("catalog_item_id"), "payload.catalog_item_id");
            require(payload.get("painting_project_id"), "payload.painting_project_id");
        }
        if (eventType.startsWith("workflow.stage.")) require(payload.get("stage"), "payload.stage");
        if ("workflow.stage.skipped".equals(eventType)) require(payload.get("reason"), "payload.reason");
        if ("workshop_recipe.created".equals(eventType)) {
            require(payload.get("catalog_item_id"), "payload.catalog_item_id");
            require(payload.get("version"), "payload.version");
            require(payload.get("display_name"), "payload.display_name");
        }
        if ("recipe.assigned".equals(eventType)) {
            require(payload.get("recipe_id"), "payload.recipe_id");
            require(payload.get("recipe_version"), "payload.recipe_version");
        }
        if ("workshop_item.comment_added".equals(eventType)) require(payload.get("comment"), "payload.comment");
        if ("workshop_item.photo_added".equals(eventType)) {
            require(payload.get("media_id"), "payload.media_id");
            require(payload.get("url"), "payload.url");
            require(payload.get("sha256"), "payload.sha256");
        }
        if ("shopping_item.status_changed".equals(eventType)) require(payload.get("checked"), "payload.checked");
    }

    private static void require(Object value, String field) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new DomainException("invalid_event", field + " is required");
        }
    }
}
