package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.workflow.DomainException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkshopRecipeProjector {
    private WorkshopRecipeProjector() {}

    public static List<WorkshopRecipeState> project(List<DomainEvent> events) {
        var states = new LinkedHashMap<String, WorkshopRecipeState>();
        for (var event : events) {
            if ("workshop_recipe.created".equals(event.eventType())) {
                states.put(event.aggregateId(), new WorkshopRecipeState(
                        event.aggregateId(), text(event.payload().get("catalog_item_id")),
                        nullable(event.payload().get("based_on_guide_id")),
                        nullable(event.payload().get("supersedes_recipe_id")),
                        text(event.payload().getOrDefault("display_name", event.aggregateId())),
                        number(event.payload().get("version")), WorkshopRecipeStatus.DRAFT,
                        listOfMaps(event.payload().get("solutions")), event.recordedAt()));
                continue;
            }
            if (!event.eventType().startsWith("workshop_recipe.")) continue;
            var current = states.get(event.aggregateId());
            if (current == null) continue;
            var status = switch (event.eventType()) {
                case "workshop_recipe.validated" -> WorkshopRecipeStatus.VALIDATED;
                case "workshop_recipe.activated" -> WorkshopRecipeStatus.ACTIVE;
                case "workshop_recipe.superseded" -> WorkshopRecipeStatus.SUPERSEDED;
                case "workshop_recipe.archived" -> WorkshopRecipeStatus.ARCHIVED;
                default -> current.status();
            };
            states.put(current.id(), new WorkshopRecipeState(
                    current.id(), current.catalogItemId(), current.basedOnGuideId(), current.supersedesRecipeId(),
                    current.displayName(), current.version(), status, current.solutions(), event.recordedAt()));
        }
        return new ArrayList<>(states.values());
    }

    public static void assertTransition(WorkshopRecipeStatus current, String action) {
        var allowed = switch (action) {
            case "validate" -> current == WorkshopRecipeStatus.DRAFT;
            case "activate" -> current == WorkshopRecipeStatus.VALIDATED;
            case "supersede" -> current == WorkshopRecipeStatus.ACTIVE;
            case "archive" -> current != WorkshopRecipeStatus.ARCHIVED;
            default -> throw new DomainException("invalid_input", "Unknown workshop recipe action: " + action);
        };
        if (!allowed) {
            throw new DomainException("invalid_transition", "Cannot " + action + " a workshop recipe currently marked " + current.id() + ".");
        }
    }

    public static String eventType(String action) {
        return switch (action) {
            case "validate" -> "workshop_recipe.validated";
            case "activate" -> "workshop_recipe.activated";
            case "supersede" -> "workshop_recipe.superseded";
            case "archive" -> "workshop_recipe.archived";
            default -> throw new DomainException("invalid_input", "Unknown workshop recipe action: " + action);
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value)); } catch (NumberFormatException ignored) { return 0; }
    }

    private static String nullable(Object value) {
        var result = text(value);
        return result.isBlank() ? null : result;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
