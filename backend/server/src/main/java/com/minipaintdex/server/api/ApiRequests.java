package com.minipaintdex.server.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.minipaintdex.domain.workshop.PaintComponent;
import com.minipaintdex.domain.workshop.RecipeSolution;
import com.minipaintdex.domain.workshop.RecipeSolutionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record AddWorkshopItemRequest(
        String itemId, @NotBlank String catalogItemId, @NotBlank String paintingProjectId,
        @NotBlank String displayName, String actorId, Instant occurredAt) {}

record CreatePaintingProjectRequest(
        @NotBlank String paintableProductId, String paintingProjectId, String name,
        String actorId, Instant occurredAt) {}

record TransitionPaintingProjectRequest(
        @NotBlank String targetStatus, String actorId, Instant occurredAt) {}

record ReplaceWorkshopPaintInventoryRequest(
        @JsonProperty("schema_version") int schemaVersion,
        @NotBlank String kind,
        @Valid List<WorkshopPaintEntryRequest> paints) {}

record WorkshopPaintEntryRequest(@JsonProperty("paint_id") @NotBlank String paintId, int quantity) {}

record TransitionStageRequest(
        @NotBlank String stage, @NotBlank String action, String comment, String reason,
        String actorId, Instant occurredAt) {}

record AddWorkshopItemCommentRequest(@NotBlank String comment, String actorId, Instant occurredAt) {}

record CreateWorkshopRecipeRequest(
        @JsonProperty("recipe_id") String recipeId,
        @JsonProperty("catalog_item_id") @NotBlank String catalogItemId,
        @JsonProperty("based_on_guide_id") String basedOnGuideId,
        @JsonProperty("supersedes_recipe_id") String supersedesRecipeId,
        @JsonProperty("display_name") @NotBlank String displayName,
        int version,
        List<RecipeSolutionRequest> solutions,
        @JsonProperty("actor_id") String actorId,
        @JsonProperty("occurred_at") Instant occurredAt) {
    CreateWorkshopRecipeRequest {
        solutions = solutions == null ? List.of() : List.copyOf(solutions);
    }
}

record RecipeSolutionRequest(
        @NotBlank String type,
        @JsonProperty("guide_slot_id") String guideSlotId,
        @JsonProperty("paint_id") String paintId,
        List<PaintComponentRequest> components,
        String instructions) {
    RecipeSolutionRequest {
        components = components == null ? List.of() : List.copyOf(components);
    }

    RecipeSolution toDomain() {
        return new RecipeSolution(
                RecipeSolutionType.fromId(type), guideSlotId, paintId,
                components.stream().map(PaintComponentRequest::toDomain).toList(), instructions);
    }
}

record PaintComponentRequest(
        @JsonProperty("paint_id") @NotBlank String paintId, Double proportion, String role) {
    PaintComponent toDomain() {
        return new PaintComponent(paintId, proportion == null ? 1 : proportion, role);
    }
}

record TransitionWorkshopRecipeRequest(
        @NotBlank String action,
        @JsonProperty("successor_recipe_id") String successorRecipeId,
        String reason,
        @JsonProperty("actor_id") String actorId,
        @JsonProperty("occurred_at") Instant occurredAt) {}

record AssignWorkshopRecipeRequest(
        @JsonProperty("recipe_id") @NotBlank String recipeId,
        @JsonProperty("actor_id") String actorId,
        @JsonProperty("occurred_at") Instant occurredAt) {}

record SetShoppingItemStatusRequest(boolean checked, String actorId, Instant occurredAt) {}

record ApplyPaintChangeSetRequest(
        @JsonProperty("schema_version") int schemaVersion,
        @NotBlank String kind,
        List<PaintOperationRequest> operations) {
    ApplyPaintChangeSetRequest {
        operations = operations == null ? List.of() : List.copyOf(operations);
    }
}

record PaintOperationRequest(
        @NotBlank String action,
        Map<String, Object> record,
        @JsonProperty("workshop_quantity_delta") Integer workshopQuantityDelta,
        @JsonProperty("confirmed_removal") Boolean confirmedRemoval) {}

record ApplyPaintableProductChangeSetRequest(
        @JsonProperty("schema_version") int schemaVersion,
        @NotBlank String kind,
        Map<String, Object> product,
        @JsonProperty("painting_guides") List<Map<String, Object>> paintingGuides,
        @JsonProperty("actor_id") String actorId,
        @JsonProperty("correlation_id") String correlationId) {
    ApplyPaintableProductChangeSetRequest {
        product = product == null ? Map.of() : Map.copyOf(product);
        paintingGuides = paintingGuides == null ? List.of() : List.copyOf(paintingGuides);
    }
}
