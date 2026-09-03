package com.minipaintdex.server.api;

import java.util.List;

import com.minipaintdex.application.command.AddWorkshopPaintableCommand;
import com.minipaintdex.application.command.AddWorkshopPaintableCommentCommand;
import com.minipaintdex.application.command.AddWorkshopPaintablePhotoCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.SetShoppingListEntryCheckedCommand;
import com.minipaintdex.application.command.TransitionWorkshopPaintableStageCommand;
import com.minipaintdex.application.command.TransitionPaintingProjectCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchPaintProductsQuery;
import com.minipaintdex.application.query.SortOrder;
import com.minipaintdex.application.result.CreatePaintingProjectResult;
import com.minipaintdex.application.usecase.WorkshopUseCases;
import com.minipaintdex.application.view.GuideReconciliationView;
import com.minipaintdex.application.view.PaintFacetsView;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
final class WorkshopController {
    private final WorkshopUseCases workshop;

    WorkshopController(WorkshopUseCases workshop) {
        this.workshop = workshop;
    }

    @GetMapping("/workshop")
    EntityModel<WorkshopResponse> workshop() {
        return EntityModel.of(new WorkshopResponse(WorkshopOverviewResponse.from(workshop.workshopOverview())),
                Link.of("/api/v1/workshop").withSelfRel(),
                Link.of("/api/v1/workshop/painting-projects").withRel("painting-projects"),
                PaintSearchResponse.postLink("/api/v1/workshop/paint-stocks/search").withRel("paint-stock-search"),
                Link.of("/api/v1/workshop/paintables").withRel("paintables"),
                Link.of("/api/v1/workshop/recipes").withRel("recipes"),
                Link.of("/api/v1/workshop/shopping-list/entries").withRel("shopping"),
                Link.of("/api/v1/activity").withRel("activity"));
    }

    @org.springframework.web.bind.annotation.PostMapping(value = "/workshop/paint-stocks/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = {MediaType.APPLICATION_JSON_VALUE, "application/hal+json"})
    @io.swagger.v3.oas.annotations.Operation(operationId = "searchWorkshopPaintStocks", summary = "Search results and/or suggestions",
            description = "Read-only MiniPaintDex contract, not Elasticsearch DSL. Body selects results, suggestions, or both. Pagination uses page/size/sort; replay the same body when following POST page links. Suggestions stay relevance-ordered and empty for blank query. Unrequested parts are null.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search response", useReturnTypeSchema = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Malformed or unknown JSON fields",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/problem+json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = org.springframework.http.ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid selection, text, filters, sorting or limit",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/problem+json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = org.springframework.http.ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Search unavailable",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/problem+json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = org.springframework.http.ProblemDetail.class)))
    EntityModel<PaintSearchResponse<com.minipaintdex.application.view.WorkshopPaintStockView>> paintSearch(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody PaintSearchRequest request,
            @ParameterObject Pageable pageable,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return PaintSearchResponse.from(workshop.searchWorkshopPaintStocks(request.toQuery(pageable, correlationId)), true);
    }

    @GetMapping("/workshop/paint-stocks/facets")
    PaintFacetsView paintFacets(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<String> brand,
            @io.swagger.v3.oas.annotations.Parameter(description = "Repeatable brand::range selection; OR with brands. Escape literal colons and backslashes with a backslash.") @RequestParam(required = false) List<String> range,
            @RequestParam(required = false) List<String> role,
            @RequestParam(required = false) List<String> applicationMethod,
            @RequestParam(required = false) List<String> applicationSystem,
            @RequestParam(required = false) List<String> color,
            @RequestParam(required = false) List<String> finish,
            @RequestParam(required = false) List<String> medium,
            @RequestParam(required = false) List<String> coverage,
            @RequestParam(required = false) List<String> effect,
            @RequestParam(required = false) List<String> undercoat,
            @RequestParam(required = false) List<String> lifecycle,
            @RequestParam(defaultValue = "false") boolean manufacturerSheetOnly,
            @RequestParam(defaultValue = "false") boolean realResultOnly) {
        return workshop.workshopPaintStockFacets(SearchPaintProductsQuery.fromSelections(
                query, brand, range, role, applicationMethod, applicationSystem,
                color, finish, medium, coverage, effect, undercoat, lifecycle),
                manufacturerSheetOnly, realResultOnly);
    }

    @GetMapping("/workshop/painting-projects")
    EntityModel<PaintingProjectsResponse> paintingProjects() {
        return EntityModel.of(new PaintingProjectsResponse(workshop.listPaintingProjects()),
                Link.of("/api/v1/workshop/painting-projects").withSelfRel(),
                Link.of("/api/v1/workshop/painting-projects").withRel("create-painting-project"),
                Link.of("/api/v1/workshop").withRel("workshop"));
    }

    @GetMapping("/workshop/painting-project-import-previews/{paintableProductId}")
    PaintingProjectImportPreviewResponse paintingProjectImportPreview(@PathVariable String paintableProductId) {
        return new PaintingProjectImportPreviewResponse(workshop.previewPaintingProjectImport(paintableProductId));
    }

    @GetMapping("/workshop/painting-guide-reconciliations/{guideId}")
    GuideReconciliationView reconcilePaintingGuide(@PathVariable String guideId) {
        return workshop.reconcileMarketPaintingGuide(guideId);
    }

    @GetMapping("/workshop/painting-projects/{paintingProjectId}")
    EntityModel<PaintingProjectResponse> paintingProject(@PathVariable String paintingProjectId) {
        var project = workshop.listPaintingProjects().stream()
                .filter(candidate -> paintingProjectId.equals(candidate.paintingProjectId()))
                .findFirst()
                .orElseThrow(() -> new com.minipaintdex.domain.shared.DomainException(
                        "not_found", "Painting project not found: " + paintingProjectId));
        var model = EntityModel.of(new PaintingProjectResponse(project),
                Link.of("/api/v1/workshop/painting-projects/" + paintingProjectId).withSelfRel(),
                Link.of("/api/v1/workshop/painting-projects").withRel("collection"),
                Link.of("/api/v1/workshop/paintables?paintingProjectId=" + paintingProjectId).withRel("paintables"));
        var transition = Link.of("/api/v1/workshop/painting-projects/" + paintingProjectId + "/transitions");
        switch (project.status()) {
            case "planned" -> model.add(
                    transition.withRel("activate"), transition.withRel("archive"));
            case "active" -> model.add(
                    transition.withRel("complete"), transition.withRel("archive"));
            case "completed" -> model.add(
                    transition.withRel("reactivate"), transition.withRel("archive"));
            default -> { }
        }
        return model;
    }

    @PostMapping("/workshop/painting-projects")
    ResponseEntity<ResultResponse<CreatePaintingProjectResult>> createPaintingProject(
            @Valid @RequestBody CreatePaintingProjectRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        var result = workshop.createPaintingProject(new CreatePaintingProjectCommand(
                request.paintableProductId(), request.paintingProjectId(), request.name(),
                request.actorId(), request.occurredAt(), correlationId, idempotencyKey));
        if (!result.applied()) return ResponseEntity.ok(new ResultResponse<>(result));
        return ResponseEntity.accepted()
                .location(ApiResponses.publicationLocation(result.publication()))
                .body(new ResultResponse<>(result));
    }

    @PostMapping("/workshop/painting-projects/{paintingProjectId}/transitions")
    ResponseEntity<PublicationResponse> transitionPaintingProject(
            @PathVariable String paintingProjectId,
            @Valid @RequestBody TransitionPaintingProjectRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.transitionPaintingProject(new TransitionPaintingProjectCommand(
                paintingProjectId, request.targetStatus(), request.actorId(), request.occurredAt(),
                correlationId, idempotencyKey)));
    }

    @GetMapping("/workshop/paintables")
    WorkshopPaintablesResponse workshopPaintables(@RequestParam(required = false) String paintingProjectId) {
        return new WorkshopPaintablesResponse(workshop.listWorkshopPaintables(paintingProjectId));
    }

    @PostMapping("/workshop/paintables")
    ResponseEntity<PublicationResponse> addWorkshopPaintable(
            @Valid @RequestBody AddWorkshopPaintableRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.addWorkshopPaintable(new AddWorkshopPaintableCommand(
                request.workshopPaintableId(), request.paintableComponentId(), request.paintingProjectId(), request.displayName(),
                request.actorId(), request.occurredAt(), correlationId, idempotencyKey)));
    }

    @GetMapping("/workshop/paintables/{workshopPaintableId}")
    EntityModel<WorkshopPaintableResponse> workshopPaintable(@PathVariable String workshopPaintableId) {
        var item = workshop.getWorkshopPaintable(workshopPaintableId);
        var itemView = item.paintable();
        var paintingProjectId = itemView.paintingProjectId();
        var model = EntityModel.of(WorkshopPaintableResponse.from(item),
                Link.of("/api/v1/workshop/paintables/" + workshopPaintableId).withSelfRel(),
                Link.of("/api/v1/workshop/paintables?paintingProjectId=" + paintingProjectId).withRel("painting-project-paintables"),
                Link.of("/api/v1/workshop/paintables/" + workshopPaintableId + "/comments").withRel("add-comment"),
                Link.of("/api/v1/workshop/paintables/" + workshopPaintableId + "/photos").withRel("add-photo"),
                Link.of("/api/v1/workshop/paintables/" + workshopPaintableId + "/recipe-assignments").withRel("assign-recipe"));
        var stage = itemView.currentStage();
        if (stage != null) {
            var status = workflowStatus(itemView.workflow(), stage);
            var transition = Link.of("/api/v1/workshop/paintables/" + workshopPaintableId + "/stage-transitions");
            if ("pending".equals(status)) {
                model.add(transition.withRel("start-stage"), transition.withRel("skip-stage"));
            }
            if ("pending".equals(status) || "in_progress".equals(status)) {
                model.add(transition.withRel("complete-stage"));
            }
        }
        return model;
    }

    @PostMapping("/workshop/paintables/{workshopPaintableId}/comments")
    ResponseEntity<PublicationResponse> addWorkshopPaintableComment(
            @PathVariable String workshopPaintableId,
            @Valid @RequestBody AddWorkshopPaintableCommentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.addWorkshopPaintableComment(new AddWorkshopPaintableCommentCommand(
                workshopPaintableId, request.comment(), request.actorId(), request.occurredAt(), correlationId, idempotencyKey)));
    }

    @PostMapping(value = "/workshop/paintables/{workshopPaintableId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<PublicationResponse> addWorkshopPaintablePhoto(
            @PathVariable String workshopPaintableId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String caption,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) Instant occurredAt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) throws IOException {
        return ApiResponses.accepted(workshop.addWorkshopPaintablePhoto(new AddWorkshopPaintablePhotoCommand(
                workshopPaintableId, file.getOriginalFilename(), file.getContentType(), file.getBytes(), stage, caption,
                actorId, occurredAt, correlationId, idempotencyKey)));
    }

    @PostMapping("/workshop/paintables/{workshopPaintableId}/stage-transitions")
    ResponseEntity<PublicationResponse> transitionWorkshopPaintableStage(
            @PathVariable String workshopPaintableId,
            @Valid @RequestBody TransitionWorkshopPaintableStageRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.transitionWorkshopPaintableStage(new TransitionWorkshopPaintableStageCommand(
                workshopPaintableId, request.stage(), request.action(), request.comment(), request.reason(),
                request.actorId(), request.occurredAt(), correlationId, idempotencyKey)));
    }

    @GetMapping("/workshop/recipes")
    WorkshopRecipesResponse workshopRecipes(@RequestParam(required = false) String paintableComponentId) {
        return new WorkshopRecipesResponse(workshop.listWorkshopRecipes(paintableComponentId));
    }

    @PostMapping("/workshop/recipes")
    ResponseEntity<PublicationResponse> createWorkshopRecipe(
            @Valid @RequestBody CreateWorkshopRecipeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.createWorkshopRecipe(new CreateWorkshopRecipeCommand(
                request.recipeId(), request.paintableComponentId(), request.basedOnGuideId(), request.supersedesRecipeId(),
                request.displayName(), request.version(),
                request.solutions().stream().map(RecipeSolutionRequest::toDomain).toList(),
                request.actorId(), request.occurredAt(), correlationId, idempotencyKey)));
    }

    @PostMapping("/workshop/recipes/{recipeId}/transitions")
    ResponseEntity<PublicationResponse> transitionWorkshopRecipe(
            @PathVariable String recipeId,
            @Valid @RequestBody TransitionWorkshopRecipeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.transitionWorkshopRecipe(new TransitionWorkshopRecipeCommand(
                recipeId, request.action(), request.successorRecipeId(), request.reason(),
                request.actorId(), request.occurredAt(),
                correlationId, idempotencyKey)));
    }

    @PostMapping("/workshop/paintables/{workshopPaintableId}/recipe-assignments")
    ResponseEntity<PublicationResponse> assignWorkshopRecipe(
            @PathVariable String workshopPaintableId,
            @Valid @RequestBody AssignWorkshopRecipeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.assignWorkshopRecipe(new AssignWorkshopRecipeCommand(
                workshopPaintableId, request.recipeId(), request.actorId(), request.occurredAt(), correlationId, idempotencyKey)));
    }

    @GetMapping("/activity")
    ActivityResponse activity(@RequestParam(required = false) String paintingProjectId) {
        return new ActivityResponse(workshop.listActivity(paintingProjectId).stream()
                .map(DomainEventResponse::from).toList());
    }

    @GetMapping("/workshop/shopping-list/entries")
    ShoppingListEntriesResponse shoppingListEntries() {
        return new ShoppingListEntriesResponse(workshop.listShoppingListEntries());
    }

    @PostMapping("/workshop/shopping-list/entries/{shoppingListEntryId}/checked")
    ResponseEntity<PublicationResponse> setShoppingListEntryChecked(
            @PathVariable String shoppingListEntryId,
            @Valid @RequestBody SetShoppingListEntryCheckedRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.setShoppingListEntryChecked(new SetShoppingListEntryCheckedCommand(
                shoppingListEntryId, request.checked(), request.actorId(), request.occurredAt(), correlationId, idempotencyKey)));
    }

    private static String workflowStatus(
            com.minipaintdex.application.view.WorkshopPaintableView.WorkflowView workflow, String stage) {
        return switch (stage) {
            case "preparation" -> workflow.preparation();
            case "priming" -> workflow.priming();
            case "pre_highlight" -> workflow.pre_highlight();
            case "painting" -> workflow.painting();
            case "finishing" -> workflow.finishing();
            case "basing" -> workflow.basing();
            default -> "";
        };
    }

    private static Link pageLink(int page, int size) {
        var uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page)
                .replaceQueryParam("size", size)
                .build(true).toUriString();
        return Link.of(uri);
    }
}
