package com.minipaintdex.adapter.file;

import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.workshop.WorkflowStage;
import com.minipaintdex.domain.workshop.PaintComponent;
import com.minipaintdex.domain.workshop.PaintingProjectCreated;
import com.minipaintdex.domain.workshop.PaintingProjectRegistered;
import com.minipaintdex.domain.workshop.PaintingProjectStatus;
import com.minipaintdex.domain.workshop.PaintingProjectStatusChanged;
import com.minipaintdex.domain.workshop.RecipeSolution;
import com.minipaintdex.domain.workshop.RecipeSolutionType;
import com.minipaintdex.domain.workshop.ShoppingItemStatusChanged;
import com.minipaintdex.domain.workshop.WorkflowStageCompleted;
import com.minipaintdex.domain.workshop.WorkflowStageReopened;
import com.minipaintdex.domain.workshop.WorkflowStageSkipped;
import com.minipaintdex.domain.workshop.WorkflowStageStarted;
import com.minipaintdex.domain.workshop.WorkshopCreated;
import com.minipaintdex.domain.workshop.WorkshopItemAdded;
import com.minipaintdex.domain.workshop.WorkshopItemCommentAdded;
import com.minipaintdex.domain.workshop.WorkshopItemPhotoAdded;
import com.minipaintdex.domain.workshop.WorkshopItemRecipeAssigned;
import com.minipaintdex.domain.workshop.WorkshopRecipeActivated;
import com.minipaintdex.domain.workshop.WorkshopRecipeArchived;
import com.minipaintdex.domain.workshop.WorkshopRecipeCreated;
import com.minipaintdex.domain.workshop.WorkshopRecipeSuperseded;
import com.minipaintdex.domain.workshop.WorkshopRecipeValidated;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit, versioned mapping between typed domain events and their JSONL representation. */
public final class DomainEventCodec {
    public EventEnvelope decode(Map<String, Object> entry) {
        var actor = map(entry.get("actor"));
        var eventType = text(entry.get("event_type"));
        var aggregateId = text(entry.get("aggregate_id"));
        var projectId = nullable(entry.get("project_id"));
        var occurredAt = instant(entry.get("occurred_at"));
        var payload = map(entry.get("payload"));
        var event = decodeEvent(eventType, aggregateId, projectId, occurredAt, payload);
        return new EventEnvelope(
                text(entry.get("event_id")),
                number(entry.getOrDefault("schema_version", 1)),
                longNumber(entry.getOrDefault("aggregate_version", 1)),
                instant(entry.get("recorded_at")),
                new Actor(text(actor.get("type")), text(actor.get("id"))),
                text(entry.get("correlation_id")),
                nullable(entry.get("causation_id")),
                nullable(entry.get("idempotency_key")),
                event);
    }

    public Map<String, Object> encode(EventEnvelope envelope) {
        var result = new LinkedHashMap<String, Object>();
        result.put("event_id", envelope.eventId());
        result.put("schema_version", envelope.schemaVersion());
        result.put("aggregate_version", envelope.aggregateVersion());
        result.put("event_type", envelope.eventType());
        result.put("occurred_at", envelope.occurredAt().toString());
        result.put("recorded_at", envelope.recordedAt().toString());
        result.put("aggregate_type", envelope.aggregateType());
        result.put("aggregate_id", envelope.aggregateId());
        if (envelope.projectId() != null) result.put("project_id", envelope.projectId());
        result.put("actor", Map.of("type", envelope.actor().type(), "id", envelope.actor().id()));
        result.put("correlation_id", envelope.correlationId());
        if (envelope.causationId() != null) result.put("causation_id", envelope.causationId());
        if (envelope.idempotencyKey() != null) result.put("idempotency_key", envelope.idempotencyKey());
        result.put("payload", encodePayload(envelope.event()));
        return result;
    }

    private DomainEvent decodeEvent(
            String type, String aggregateId, String projectId, Instant at, Map<String, Object> payload) {
        return switch (type) {
            case "workshop.created" -> new WorkshopCreated(
                    aggregateId, text(payload.get("name")), at);
            case "workshop.painting_project_registered" -> new PaintingProjectRegistered(
                    aggregateId, text(payload.get("painting_project_id")), at);
            case "painting_project.created" -> new PaintingProjectCreated(
                    aggregateId, text(payload.get("workshop_id")), text(payload.get("paintable_product_id")),
                    text(payload.get("name")), number(payload.get("paintable_item_count")), at);
            case "painting_project.status_changed" -> new PaintingProjectStatusChanged(
                    aggregateId, PaintingProjectStatus.fromId(text(payload.get("status"))), at);
            case "workshop_item.added" -> new WorkshopItemAdded(
                    aggregateId, text(payload.get("catalog_item_id")), requiredProject(projectId, payload),
                    text(payload.get("display_name")), number(payload.get("ordinal")), at);
            case "workshop_item.comment_added" -> new WorkshopItemCommentAdded(
                    aggregateId, requiredProject(projectId, payload), text(payload.get("comment")), at);
            case "workshop_item.photo_added" -> new WorkshopItemPhotoAdded(
                    aggregateId, requiredProject(projectId, payload), text(payload.get("media_id")),
                    text(payload.get("url")), text(payload.get("stage")), text(payload.get("caption")),
                    text(payload.get("original_filename")), text(payload.get("content_type")),
                    longNumber(payload.get("size")), text(payload.get("sha256")), at);
            case "recipe.assigned" -> new WorkshopItemRecipeAssigned(
                    aggregateId, requiredProject(projectId, payload), text(payload.get("recipe_id")),
                    number(payload.get("recipe_version")), at);
            case "workflow.stage.started" -> new WorkflowStageStarted(
                    aggregateId, requiredProject(projectId, payload), stage(payload), nullable(payload.get("comment")), at);
            case "workflow.stage.completed" -> new WorkflowStageCompleted(
                    aggregateId, requiredProject(projectId, payload), stage(payload), nullable(payload.get("comment")), at);
            case "workflow.stage.skipped" -> new WorkflowStageSkipped(
                    aggregateId, requiredProject(projectId, payload), stage(payload), text(payload.get("reason")), at);
            case "workflow.stage.reopened" -> new WorkflowStageReopened(
                    aggregateId, requiredProject(projectId, payload), stage(payload), nullable(payload.get("comment")), at);
            case "workshop_recipe.created" -> new WorkshopRecipeCreated(
                    aggregateId, requiredProject(projectId, payload), text(payload.get("catalog_item_id")),
                    nullable(payload.get("based_on_guide_id")), nullable(payload.get("supersedes_recipe_id")),
                    text(payload.get("display_name")), number(payload.get("version")),
                    listOfMaps(payload.get("solutions")).stream().map(this::decodeSolution).toList(), at);
            case "workshop_recipe.validated" -> new WorkshopRecipeValidated(
                    aggregateId, requiredProject(projectId, payload), at);
            case "workshop_recipe.activated" -> new WorkshopRecipeActivated(
                    aggregateId, requiredProject(projectId, payload), at);
            case "workshop_recipe.superseded" -> new WorkshopRecipeSuperseded(
                    aggregateId, requiredProject(projectId, payload), text(payload.get("successor_recipe_id")), at);
            case "workshop_recipe.archived" -> new WorkshopRecipeArchived(
                    aggregateId, requiredProject(projectId, payload), nullable(payload.get("reason")), at);
            case "shopping_item.status_changed" -> new ShoppingItemStatusChanged(
                    aggregateId, Boolean.TRUE.equals(payload.get("checked")), at);
            default -> throw new FileStorageException("Unknown domain event type: " + type, null);
        };
    }

    private Map<String, Object> encodePayload(DomainEvent event) {
        var payload = new LinkedHashMap<String, Object>();
        switch (event) {
            case WorkshopCreated value -> payload.put("name", value.name());
            case PaintingProjectRegistered value -> payload.put("painting_project_id", value.paintingProjectId());
            case PaintingProjectCreated value -> {
                payload.put("workshop_id", value.workshopId());
                payload.put("paintable_product_id", value.paintableProductId());
                payload.put("name", value.name());
                payload.put("paintable_item_count", value.paintableItemCount());
            }
            case PaintingProjectStatusChanged value -> payload.put("status", value.status().id());
            case WorkshopItemAdded value -> {
                payload.put("catalog_item_id", value.catalogItemId());
                payload.put("painting_project_id", value.paintingProjectId());
                payload.put("display_name", value.displayName());
                payload.put("ordinal", value.ordinal());
            }
            case WorkshopItemCommentAdded value -> {
                payload.put("painting_project_id", value.paintingProjectId());
                payload.put("comment", value.comment());
            }
            case WorkshopItemPhotoAdded value -> {
                payload.put("painting_project_id", value.paintingProjectId());
                payload.put("media_id", value.mediaId());
                payload.put("url", value.url());
                optional(payload, "stage", value.stage());
                optional(payload, "caption", value.caption());
                payload.put("original_filename", value.originalFilename());
                payload.put("content_type", value.contentType());
                payload.put("size", value.size());
                payload.put("sha256", value.sha256());
            }
            case WorkshopItemRecipeAssigned value -> {
                payload.put("painting_project_id", value.paintingProjectId());
                payload.put("recipe_id", value.recipeId());
                payload.put("recipe_version", value.recipeVersion());
            }
            case WorkflowStageStarted value -> stagePayload(payload, value.paintingProjectId(), value.stage(), "comment", value.comment());
            case WorkflowStageCompleted value -> stagePayload(payload, value.paintingProjectId(), value.stage(), "comment", value.comment());
            case WorkflowStageSkipped value -> stagePayload(payload, value.paintingProjectId(), value.stage(), "reason", value.reason());
            case WorkflowStageReopened value -> stagePayload(payload, value.paintingProjectId(), value.stage(), "comment", value.comment());
            case WorkshopRecipeCreated value -> {
                payload.put("painting_project_id", value.paintingProjectId());
                payload.put("catalog_item_id", value.catalogItemId());
                optional(payload, "based_on_guide_id", value.basedOnGuideId());
                optional(payload, "supersedes_recipe_id", value.supersedesRecipeId());
                payload.put("display_name", value.displayName());
                payload.put("version", value.recipeVersion());
                payload.put("solutions", value.solutions().stream().map(this::encodeSolution).toList());
            }
            case WorkshopRecipeValidated value -> payload.put("painting_project_id", value.paintingProjectId());
            case WorkshopRecipeActivated value -> payload.put("painting_project_id", value.paintingProjectId());
            case WorkshopRecipeSuperseded value -> {
                payload.put("painting_project_id", value.paintingProjectId());
                payload.put("successor_recipe_id", value.successorRecipeId());
            }
            case WorkshopRecipeArchived value -> {
                payload.put("painting_project_id", value.paintingProjectId());
                optional(payload, "reason", value.reason());
            }
            case ShoppingItemStatusChanged value -> payload.put("checked", value.checked());
            default -> throw new FileStorageException("Unsupported domain event: " + event.getClass().getName(), null);
        }
        return payload;
    }

    private RecipeSolution decodeSolution(Map<String, Object> payload) {
        return new RecipeSolution(
                RecipeSolutionType.fromId(text(payload.get("type"))),
                nullable(payload.get("guide_slot_id")),
                nullable(payload.get("paint_id")),
                listOfMaps(payload.get("components")).stream().map(component -> new PaintComponent(
                        text(component.get("paint_id")), doubleNumber(component.getOrDefault("proportion", 1)),
                        nullable(component.get("role")))).toList(),
                nullable(payload.get("instructions")));
    }

    private Map<String, Object> encodeSolution(RecipeSolution solution) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("type", solution.type().id());
        optional(payload, "guide_slot_id", solution.guideSlotId());
        optional(payload, "paint_id", solution.paintId());
        if (!solution.components().isEmpty()) payload.put("components", solution.components().stream().map(component -> {
            var value = new LinkedHashMap<String, Object>();
            value.put("paint_id", component.paintId());
            value.put("proportion", component.proportion());
            optional(value, "role", component.role());
            return value;
        }).toList());
        optional(payload, "instructions", solution.instructions());
        return payload;
    }

    private static void stagePayload(
            Map<String, Object> payload, String projectId, WorkflowStage stage, String noteName, String note) {
        payload.put("painting_project_id", projectId);
        payload.put("stage", stage.id());
        optional(payload, noteName, note);
    }

    private static WorkflowStage stage(Map<String, Object> payload) {
        return WorkflowStage.fromId(text(payload.get("stage")));
    }

    private static String requiredProject(String projectId, Map<String, Object> payload) {
        var result = projectId == null ? text(payload.get("painting_project_id")) : projectId;
        if (result.isBlank()) throw new FileStorageException("Event project_id is required.", null);
        return result;
    }

    private static void optional(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(DomainEventCodec::map).toList();
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String nullable(Object value) { var valueText = text(value); return valueText.isBlank() ? null : valueText; }
    private static int number(Object value) { return (int) longNumber(value); }
    private static long longNumber(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(text(value)); } catch (NumberFormatException ignored) { return 0; }
    }
    private static double doubleNumber(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(text(value)); } catch (NumberFormatException ignored) { return 0; }
    }
    private static Instant instant(Object value) { return Instant.parse(text(value)); }
}
