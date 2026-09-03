package com.minipaintdex.server.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.minipaintdex.application.event.PublicationReceipt;
import com.minipaintdex.application.view.PaintProductView;
import com.minipaintdex.application.view.MarketPaintingGuideView;
import com.minipaintdex.application.view.PaintableProductSummaryView;
import com.minipaintdex.application.view.PaintableProductView;
import com.minipaintdex.application.view.PaintModelView;
import com.minipaintdex.application.view.PaintingProjectView;
import com.minipaintdex.application.view.PaintingProjectImportPreviewView;
import com.minipaintdex.application.view.RebuildProjectionResult;
import com.minipaintdex.application.view.ShoppingListEntryView;
import com.minipaintdex.application.view.WorkshopPaintableView;
import com.minipaintdex.application.view.WorkshopOverviewView;
import com.minipaintdex.application.view.WorkshopRecipeView;
import com.minipaintdex.application.view.WorkshopPaintStockView;
import com.minipaintdex.domain.event.EventEnvelope;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;
import java.util.Map;

record PaintModelSchemaResponse(
        @JsonProperty("$schema") String schema,
        @JsonProperty("$id") String id,
        String title,
        String type,
        boolean additionalProperties,
        List<String> required,
        Map<String, Object> properties,
        @JsonProperty("x-model-version") int modelVersion,
        @JsonProperty("x-filters") List<PaintModelView.Filter> filters,
        @JsonProperty("x-sort-options") List<PaintModelView.SortOption> sortOptions,
        @JsonProperty("x-vocabularies") Map<String, List<String>> vocabularies,
        @JsonProperty("x-image-quality-ranks") Map<String, Integer> imageQualityRanks) {}

record PaintableProductsResponse(List<PaintableProductSummaryView> paintableProducts) {}
record PaintableProductResponse(PaintableProductView paintableProduct) {}
record PaintingGuidesResponse(List<MarketPaintingGuideView> paintingGuides) {}
record WorkshopResponse(WorkshopOverviewResponse workshop) {}
record PaintingProjectsResponse(List<PaintingProjectView> paintingProjects) {}
record PaintingProjectResponse(PaintingProjectView paintingProject) {}
record PaintingProjectImportPreviewResponse(PaintingProjectImportPreviewView preview) {}
record WorkshopPaintablesResponse(List<WorkshopPaintableView> paintables) {}
record WorkshopRecipesResponse(List<WorkshopRecipeView> recipes) {}
record ShoppingListEntriesResponse(List<ShoppingListEntryView> entries) {}
record ActivityResponse(List<DomainEventResponse> events) {}
record PublicationResponse(PublicationReceipt publication) {}
record ResultResponse<T>(T result) {}
record ProjectionResponse(RebuildProjectionResult projection) {}

record WorkshopPaintableResponse(
        String id,
        String paintableComponentId,
        String paintingProjectId,
        String displayName,
        WorkshopPaintableView.WorkflowView workflow,
        String currentStage,
        boolean completed,
        String recipeId,
        int recipeVersion,
        java.time.Instant updatedAt,
        List<DomainEventResponse> activity) {
    static WorkshopPaintableResponse from(WorkshopPaintableView.Detail detail) {
        var item = detail.paintable();
        return new WorkshopPaintableResponse(
                item.id(), item.paintableComponentId(), item.paintingProjectId(), item.displayName(),
                item.workflow(), item.currentStage(), item.completed(), item.recipeId(), item.recipeVersion(),
                item.updatedAt(), detail.activity().stream().map(DomainEventResponse::from).toList());
    }
}

record DomainEventResponse(
        String eventId,
        String eventType,
        java.time.Instant occurredAt,
        java.time.Instant recordedAt,
        String aggregateType,
        String aggregateId,
        String paintingProjectId,
        Object payload) {
    static DomainEventResponse from(EventEnvelope envelope) {
        return new DomainEventResponse(
                envelope.eventId(), envelope.eventType(), envelope.occurredAt(), envelope.recordedAt(),
                envelope.aggregateType(), envelope.aggregateId(), envelope.scopePaintingProjectId(), envelope.event());
    }
}

record WorkshopOverviewResponse(
        String id,
        List<PaintingProjectView> paintingProjects,
        int projectCount,
        int paintableCount,
        int completedPaintableCount,
        int progressPercentage,
        List<DomainEventResponse> recentActivity) {
    static WorkshopOverviewResponse from(WorkshopOverviewView view) {
        return new WorkshopOverviewResponse(
                view.id(), view.paintingProjects(), view.projectCount(), view.paintableCount(),
                view.completedPaintableCount(), view.progressPercentage(),
                view.recentActivity().stream().map(DomainEventResponse::from).toList());
    }
}

final class ApiResponses {
    private ApiResponses() {}

    static ResponseEntity<PublicationResponse> accepted(PublicationReceipt publication) {
        return ResponseEntity.accepted()
                .location(publicationLocation(publication))
                .body(new PublicationResponse(publication));
    }

    static URI publicationLocation(PublicationReceipt publication) {
        return URI.create("/api/v1/publications/" + publication.publicationId());
    }
}
