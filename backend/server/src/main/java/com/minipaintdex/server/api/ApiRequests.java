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

record AddWorkshopPaintableRequest(
        String workshopPaintableId, @NotBlank String paintableComponentId, @NotBlank String paintingProjectId,
        @NotBlank String displayName, String actorId, Instant occurredAt) {}

record CreatePaintingProjectRequest(
        @NotBlank String paintableProductId, String paintingProjectId, String name,
        String actorId, Instant occurredAt) {}

record TransitionPaintingProjectRequest(
        @NotBlank String targetStatus, String actorId, Instant occurredAt) {}



record TransitionWorkshopPaintableStageRequest(
        @NotBlank String stage, @NotBlank String action, String comment, String reason,
        String actorId, Instant occurredAt) {}

record AddWorkshopPaintableCommentRequest(@NotBlank String comment, String actorId, Instant occurredAt) {}

record CreateWorkshopRecipeRequest(
        String recipeId,
        @NotBlank String paintableComponentId,
        String basedOnGuideId,
        String supersedesRecipeId,
        @NotBlank String displayName,
        int version,
        List<RecipeSolutionRequest> solutions,
        String actorId,
        Instant occurredAt) {
    CreateWorkshopRecipeRequest {
        solutions = solutions == null ? List.of() : List.copyOf(solutions);
    }
}

record RecipeSolutionRequest(
        @NotBlank String type,
        String guideSlotId,
        String paintProductId,
        List<PaintComponentRequest> components,
        String instructions) {
    RecipeSolutionRequest {
        components = components == null ? List.of() : List.copyOf(components);
    }

    RecipeSolution toDomain() {
        return new RecipeSolution(
                RecipeSolutionType.fromId(type), guideSlotId, paintProductId,
                components.stream().map(PaintComponentRequest::toDomain).toList(), instructions);
    }
}

record PaintComponentRequest(
        @NotBlank String paintProductId, Double proportion, String role) {
    PaintComponent toDomain() {
        return new PaintComponent(paintProductId, proportion == null ? 1 : proportion, role);
    }
}

record TransitionWorkshopRecipeRequest(
        @NotBlank String action,
        String successorRecipeId,
        String reason,
        String actorId,
        Instant occurredAt) {}

record AssignWorkshopRecipeRequest(
        @NotBlank String recipeId,
        String actorId,
        Instant occurredAt) {}

record SetShoppingListEntryCheckedRequest(boolean checked, String actorId, Instant occurredAt) {}

record ApplyPaintChangeSetRequest(
        @JsonProperty("schema_version") int schemaVersion,
        @NotBlank String kind,
        List<PaintOperationRequest> operations,
        @JsonProperty("catalog_editions") List<Map<String, Object>> catalogEditions,
        @JsonProperty("paint_usage_guides") List<Map<String, Object>> paintUsageGuides) {
    ApplyPaintChangeSetRequest {
        operations = operations == null ? List.of() : List.copyOf(operations);
        catalogEditions = catalogEditions == null ? List.of() : List.copyOf(catalogEditions);
        paintUsageGuides = paintUsageGuides == null ? List.of() : List.copyOf(paintUsageGuides);
    }
}

record PaintOperationRequest(
        @NotBlank String action,
        @JsonProperty("previous_id") String previousId,
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
