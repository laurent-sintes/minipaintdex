package com.minipaintdex.server.api;

import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.AddWorkshopItemCommentCommand;
import com.minipaintdex.application.command.AddWorkshopItemPhotoCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.SetShoppingItemStatusCommand;
import com.minipaintdex.application.command.TransitionStageCommand;
import com.minipaintdex.application.command.TransitionPaintingProjectCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
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
                Link.of("/api/v1/workshop/paints").withRel("paints"),
                Link.of("/api/v1/workshop/items").withRel("items"),
                Link.of("/api/v1/workshop/recipes").withRel("recipes"),
                Link.of("/api/v1/shopping/items").withRel("shopping"),
                Link.of("/api/v1/activity").withRel("activity"));
    }

    @GetMapping("/workshop/paints")
    EntityModel<WorkshopPaintPageResponse> paints(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String applicationMethod,
            @RequestParam(required = false) String applicationSystem,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String finish,
            @RequestParam(required = false) String medium,
            @RequestParam(required = false) String coverage,
            @RequestParam(required = false) String effect,
            @RequestParam(required = false) String undercoat,
            @RequestParam(required = false) String lifecycle,
            @RequestParam(defaultValue = "false") boolean manufacturerSheetOnly,
            @RequestParam(defaultValue = "false") boolean realResultOnly,
            @ParameterObject Pageable pageable) {
        var filters = new SearchMarketPaintsQuery(
                query, brand, range, role, applicationMethod, applicationSystem,
                color, finish, medium, coverage, effect, undercoat, lifecycle);
        var pageQuery = new PageQuery(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().stream()
                .map(order -> new SortOrder(order.getProperty(), order.isAscending()
                        ? SortOrder.Direction.ASCENDING : SortOrder.Direction.DESCENDING))
                .toList());
        var result = workshop.searchWorkshopPaintPage(
                filters, manufacturerSheetOnly, realResultOnly, pageQuery);
        var response = new WorkshopPaintPageResponse(
                result.content(), result.totalElements(), result.page(), result.size(), result.totalPages());
        var model = EntityModel.of(response, pageLink(result.page(), result.size()).withSelfRel());
        model.add(pageLink(0, result.size()).withRel("first"));
        if (result.hasPrevious()) model.add(pageLink(result.page() - 1, result.size()).withRel("prev"));
        if (result.hasNext()) model.add(pageLink(result.page() + 1, result.size()).withRel("next"));
        model.add(pageLink(Math.max(0, result.totalPages() - 1), result.size()).withRel("last"));
        model.add(Link.of("/api/v1/market/paints").withRel("market-catalog"));
        return model;
    }

    @GetMapping("/workshop/paints/facets")
    PaintFacetsView paintFacets(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String applicationMethod,
            @RequestParam(required = false) String applicationSystem,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String finish,
            @RequestParam(required = false) String medium,
            @RequestParam(required = false) String coverage,
            @RequestParam(required = false) String effect,
            @RequestParam(required = false) String undercoat,
            @RequestParam(required = false) String lifecycle,
            @RequestParam(defaultValue = "false") boolean manufacturerSheetOnly,
            @RequestParam(defaultValue = "false") boolean realResultOnly) {
        return workshop.workshopPaintFacets(new SearchMarketPaintsQuery(
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

    @GetMapping("/workshop/painting-project-import-previews/{productId}")
    ProductImportPreviewResponse paintingProjectImportPreview(@PathVariable String productId) {
        return new ProductImportPreviewResponse(workshop.previewProductImport(productId));
    }

    @GetMapping("/workshop/painting-guide-reconciliations/{guideId}")
    GuideReconciliationView reconcilePaintingGuide(@PathVariable String guideId) {
        return workshop.reconcileMarketPaintingGuide(guideId);
    }

    @GetMapping("/workshop/painting-projects/{paintingProjectId}")
    EntityModel<PaintingProjectResponse> paintingProject(@PathVariable String paintingProjectId) {
        var project = workshop.listPaintingProjects().stream()
                .filter(candidate -> paintingProjectId.equals(candidate.projectId()))
                .findFirst()
                .orElseThrow(() -> new com.minipaintdex.domain.shared.DomainException(
                        "not_found", "Painting project not found: " + paintingProjectId));
        var model = EntityModel.of(new PaintingProjectResponse(project),
                Link.of("/api/v1/workshop/painting-projects/" + paintingProjectId).withSelfRel(),
                Link.of("/api/v1/workshop/painting-projects").withRel("collection"),
                Link.of("/api/v1/workshop/items?paintingProjectId=" + paintingProjectId).withRel("items"));
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

    @GetMapping("/workshop/items")
    WorkshopItemsResponse workshopItems(@RequestParam(required = false) String paintingProjectId) {
        return new WorkshopItemsResponse(workshop.listWorkshopItems(paintingProjectId));
    }

    @PostMapping("/workshop/items")
    ResponseEntity<PublicationResponse> addWorkshopItem(
            @Valid @RequestBody AddWorkshopItemRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.addWorkshopItem(new AddWorkshopItemCommand(
                request.itemId(), request.catalogItemId(), request.paintingProjectId(), request.displayName(),
                request.actorId(), request.occurredAt(), correlationId, idempotencyKey)));
    }

    @GetMapping("/workshop/items/{itemId}")
    EntityModel<WorkshopItemResponse> workshopItem(@PathVariable String itemId) {
        var item = workshop.getWorkshopItem(itemId);
        var itemView = item.item();
        var projectId = itemView.paintingProjectId();
        var model = EntityModel.of(WorkshopItemResponse.from(item),
                Link.of("/api/v1/workshop/items/" + itemId).withSelfRel(),
                Link.of("/api/v1/workshop/items?paintingProjectId=" + projectId).withRel("painting-project-items"),
                Link.of("/api/v1/workshop/items/" + itemId + "/comments").withRel("add-comment"),
                Link.of("/api/v1/workshop/items/" + itemId + "/photos").withRel("add-photo"),
                Link.of("/api/v1/workshop/items/" + itemId + "/recipe-assignments").withRel("assign-recipe"));
        var stage = itemView.currentStage();
        if (stage != null) {
            var status = workflowStatus(itemView.workflow(), stage);
            var transition = Link.of("/api/v1/workshop/items/" + itemId + "/stage-transitions");
            if ("pending".equals(status)) {
                model.add(transition.withRel("start-stage"), transition.withRel("skip-stage"));
            }
            if ("pending".equals(status) || "in_progress".equals(status)) {
                model.add(transition.withRel("complete-stage"));
            }
        }
        return model;
    }

    @PostMapping("/workshop/items/{itemId}/comments")
    ResponseEntity<PublicationResponse> addWorkshopItemComment(
            @PathVariable String itemId,
            @Valid @RequestBody AddWorkshopItemCommentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.addWorkshopItemComment(new AddWorkshopItemCommentCommand(
                itemId, request.comment(), request.actorId(), request.occurredAt(), correlationId, idempotencyKey)));
    }

    @PostMapping(value = "/workshop/items/{itemId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<PublicationResponse> addWorkshopItemPhoto(
            @PathVariable String itemId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String caption,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) Instant occurredAt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) throws IOException {
        return ApiResponses.accepted(workshop.addWorkshopItemPhoto(new AddWorkshopItemPhotoCommand(
                itemId, file.getOriginalFilename(), file.getContentType(), file.getBytes(), stage, caption,
                actorId, occurredAt, correlationId, idempotencyKey)));
    }

    @PostMapping("/workshop/items/{itemId}/stage-transitions")
    ResponseEntity<PublicationResponse> transitionStage(
            @PathVariable String itemId,
            @Valid @RequestBody TransitionStageRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.transitionStage(new TransitionStageCommand(
                itemId, request.stage(), request.action(), request.comment(), request.reason(),
                request.actorId(), request.occurredAt(), correlationId, idempotencyKey)));
    }

    @GetMapping("/workshop/recipes")
    WorkshopRecipesResponse workshopRecipes(@RequestParam(required = false) String catalogItemId) {
        return new WorkshopRecipesResponse(workshop.listWorkshopRecipes(catalogItemId));
    }

    @PostMapping("/workshop/recipes")
    ResponseEntity<PublicationResponse> createWorkshopRecipe(
            @Valid @RequestBody CreateWorkshopRecipeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.createWorkshopRecipe(new CreateWorkshopRecipeCommand(
                request.recipeId(), request.catalogItemId(), request.basedOnGuideId(), request.supersedesRecipeId(),
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

    @PostMapping("/workshop/items/{itemId}/recipe-assignments")
    ResponseEntity<PublicationResponse> assignWorkshopRecipe(
            @PathVariable String itemId,
            @Valid @RequestBody AssignWorkshopRecipeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.assignWorkshopRecipe(new AssignWorkshopRecipeCommand(
                itemId, request.recipeId(), request.actorId(), request.occurredAt(), correlationId, idempotencyKey)));
    }

    @GetMapping("/activity")
    ActivityResponse activity(@RequestParam(required = false) String paintingProjectId) {
        return new ActivityResponse(workshop.listActivity(paintingProjectId).stream()
                .map(DomainEventResponse::from).toList());
    }

    @GetMapping("/shopping/items")
    ShoppingItemsResponse shoppingItems() {
        return new ShoppingItemsResponse(workshop.listShoppingItems());
    }

    @PostMapping("/shopping/items/{itemId}/status")
    ResponseEntity<PublicationResponse> setShoppingItemStatus(
            @PathVariable String itemId,
            @Valid @RequestBody SetShoppingItemStatusRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ApiResponses.accepted(workshop.setShoppingItemStatus(new SetShoppingItemStatusCommand(
                itemId, request.checked(), request.actorId(), request.occurredAt(), correlationId, idempotencyKey)));
    }

    private static String workflowStatus(
            com.minipaintdex.application.view.WorkshopItemView.WorkflowView workflow, String stage) {
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
