package com.minipaintdex.server.api;

import com.minipaintdex.application.MiniPaintDexService;
import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.AddWorkshopItemCommentCommand;
import com.minipaintdex.application.command.AddWorkshopItemPhotoCommand;
import com.minipaintdex.application.command.ApplyMarketPaintChangeSetCommand;
import com.minipaintdex.application.command.ApplyMarketPaintableProductChangeSetCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.command.TransitionStageCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.command.SetShoppingItemStatusCommand;
import com.minipaintdex.application.command.ReplaceWorkshopPaintInventoryCommand;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
class MiniPaintDexController {
    private final MiniPaintDexService service;

    MiniPaintDexController(MiniPaintDexService service) {
        this.service = service;
    }

    @GetMapping("/health")
    Map<String, Object> health() {
        return service.health();
    }

    @GetMapping("/bootstrap")
    Map<String, Object> bootstrap(@RequestParam(defaultValue = "true") boolean includeMarketPaints) {
        return service.bootstrap(includeMarketPaints);
    }

    @GetMapping("/site/config")
    Object siteConfig() {
        return service.siteConfiguration();
    }

    @GetMapping("/market/paints")
    Map<String, Object> paints(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String finish,
            @RequestParam(required = false) String medium,
            @RequestParam(required = false) String opacity,
            @RequestParam(required = false) String volume,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) String lifecycle,
            @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "false") boolean ownedOnly,
            @RequestParam(defaultValue = "false") boolean manufacturerSheetOnly,
            @RequestParam(defaultValue = "false") boolean realResultOnly,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit) {
        var filters = new SearchMarketPaintsQuery(
                query, brand, range, type, color, finish, medium, opacity, volume, reference, lifecycle, manufacturer, tag);
        if (offset != null || limit != null) {
            return service.searchMarketPaintPage(filters, ownedOnly, manufacturerSheetOnly, realResultOnly,
                    offset == null ? 0 : offset, limit == null ? 60 : limit);
        }
        return Map.of("paints", service.searchMarketPaints(filters));
    }

    @GetMapping("/market/paints/facets")
    Map<String, Object> paintFacets(@RequestParam(defaultValue = "false") boolean ownedOnly) {
        return service.marketPaintFacets(ownedOnly);
    }

    @PostMapping("/market/paint-changesets")
    Map<String, Object> applyPaintChangeSet(
            @Valid @RequestBody ApplyPaintChangeSetRequest request,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        var operations = request.operations().stream()
                .map(operation -> new ApplyMarketPaintChangeSetCommand.Operation(
                        operation.action(), operation.record(),
                        operation.workshopQuantityDelta() == null ? 0 : operation.workshopQuantityDelta(),
                        Boolean.TRUE.equals(operation.confirmedRemoval())))
                .toList();
        var result = service.applyMarketPaintChangeSet(new ApplyMarketPaintChangeSetCommand(
                request.schemaVersion(), request.kind(), operations, dryRun));
        return Map.of("result", result);
    }

    @PostMapping("/market/paintable-product-changesets")
    Map<String, Object> applyPaintableProductChangeSet(
            @Valid @RequestBody ApplyPaintableProductChangeSetRequest request,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        var result = service.applyMarketPaintableProductChangeSet(new ApplyMarketPaintableProductChangeSetCommand(
                request.schemaVersion(), request.kind(), request.product(), request.paintingGuides(),
                dryRun, request.actorId(), request.correlationId()));
        return Map.of("result", result);
    }

    @GetMapping("/market/paints/{paintId}")
    Map<String, Object> paint(@PathVariable String paintId) {
        return Map.of("paint", service.getMarketPaint(paintId));
    }

    @GetMapping("/market/paintable-products")
    Map<String, Object> paintableProducts() {
        return Map.of("paintableProducts", service.listMarketPaintableProducts());
    }

    @GetMapping("/market/paintable-products/{productId}")
    Map<String, Object> paintableProduct(@PathVariable String productId) {
        return Map.of("paintableProduct", service.getMarketPaintableProduct(productId));
    }

    @GetMapping("/market/paintable-products/{productId}/workshop-import-preview")
    Map<String, Object> paintableProductImportPreview(@PathVariable String productId) {
        return Map.of("preview", service.previewProductImport(productId));
    }

    @GetMapping("/market/painting-guides")
    Map<String, Object> paintingGuides(@RequestParam(required = false) String catalogItemId) {
        return Map.of("paintingGuides", service.listMarketPaintingGuides(catalogItemId));
    }

    @GetMapping("/market/painting-guides/{guideId}/reconciliation")
    Map<String, Object> reconcilePaintingGuide(@PathVariable String guideId) {
        return service.reconcileMarketPaintingGuide(guideId);
    }

    @GetMapping("/workshop")
    Map<String, Object> workshop() {
        return Map.of("workshop", service.workshopOverview());
    }

    @PostMapping("/workshop/paint-inventory-imports")
    Map<String, Object> replaceWorkshopPaintInventory(
            @Valid @RequestBody ReplaceWorkshopPaintInventoryRequest request,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        var entries = request.paints().stream()
                .map(entry -> new ReplaceWorkshopPaintInventoryCommand.Entry(entry.paintId(), entry.quantity()))
                .toList();
        return Map.of("result", service.replaceWorkshopPaintInventory(
                new ReplaceWorkshopPaintInventoryCommand(
                        request.schemaVersion(), request.kind(), entries, dryRun)));
    }

    @GetMapping("/workshop/painting-projects")
    Map<String, Object> paintingProjects() {
        return Map.of("paintingProjects", service.listPaintingProjects());
    }

    @PostMapping("/workshop/painting-projects")
    ResponseEntity<Map<String, Object>> createPaintingProject(
            @Valid @RequestBody CreatePaintingProjectRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        var result = service.createPaintingProject(new CreatePaintingProjectCommand(
                request.paintableProductId(), request.paintingProjectId(), request.name(),
                request.actorId(), request.occurredAt(), correlationId, idempotencyKey));
        return ResponseEntity.status(result.applied() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(Map.of("result", result));
    }

    @GetMapping("/workshop/items")
    Map<String, Object> workshopItems(@RequestParam(required = false) String paintingProjectId) {
        return Map.of("items", service.listWorkshopItems(paintingProjectId));
    }

    @PostMapping("/workshop/items")
    ResponseEntity<Map<String, Object>> addWorkshopItem(
            @Valid @RequestBody AddWorkshopItemRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        var event = service.addWorkshopItem(new AddWorkshopItemCommand(
                request.itemId(), request.catalogItemId(), request.paintingProjectId(), request.displayName(),
                request.actorId(), request.occurredAt(), correlationId, idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("event", event));
    }

    @GetMapping("/workshop/items/{itemId}")
    Map<String, Object> workshopItem(@PathVariable String itemId) {
        return Map.of("item", service.getWorkshopItem(itemId));
    }

    @PostMapping("/workshop/items/{itemId}/comments")
    ResponseEntity<Map<String, Object>> addWorkshopItemComment(
            @PathVariable String itemId,
            @Valid @RequestBody AddWorkshopItemCommentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        var event = service.addWorkshopItemComment(new AddWorkshopItemCommentCommand(
                itemId, request.comment(), request.actorId(), request.occurredAt(), correlationId, idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("event", event));
    }

    @PostMapping(value = "/workshop/items/{itemId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Map<String, Object>> addWorkshopItemPhoto(
            @PathVariable String itemId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String caption,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) Instant occurredAt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) throws IOException {
        var event = service.addWorkshopItemPhoto(new AddWorkshopItemPhotoCommand(
                itemId, file.getOriginalFilename(), file.getContentType(), file.getBytes(), stage, caption,
                actorId, occurredAt, correlationId, idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("event", event));
    }

    @PostMapping("/workshop/items/{itemId}/stage-transitions")
    ResponseEntity<Map<String, Object>> transitionStage(
            @PathVariable String itemId,
            @Valid @RequestBody TransitionStageRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        var event = service.transitionStage(new TransitionStageCommand(
                itemId, request.stage(), request.action(), request.comment(), request.reason(),
                request.actorId(), request.occurredAt(), correlationId, idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("event", event));
    }

    @GetMapping("/workshop/recipes")
    Map<String, Object> workshopRecipes(@RequestParam(required = false) String catalogItemId) {
        return Map.of("recipes", service.listWorkshopRecipes(catalogItemId));
    }

    @PostMapping("/workshop/recipes")
    ResponseEntity<Map<String, Object>> createWorkshopRecipe(
            @Valid @RequestBody CreateWorkshopRecipeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        var event = service.createWorkshopRecipe(new CreateWorkshopRecipeCommand(
                request.recipeId(), request.catalogItemId(), request.basedOnGuideId(), request.supersedesRecipeId(),
                request.displayName(), request.version(), request.solutions(), request.actorId(), request.occurredAt(),
                correlationId, idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("event", event));
    }

    @PostMapping("/workshop/recipes/{recipeId}/transitions")
    ResponseEntity<Map<String, Object>> transitionWorkshopRecipe(
            @PathVariable String recipeId,
            @Valid @RequestBody TransitionWorkshopRecipeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        var event = service.transitionWorkshopRecipe(new TransitionWorkshopRecipeCommand(
                recipeId, request.action(), request.comment(), request.actorId(), request.occurredAt(), correlationId, idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("event", event));
    }

    @PostMapping("/workshop/items/{itemId}/recipe-assignments")
    ResponseEntity<Map<String, Object>> assignWorkshopRecipe(
            @PathVariable String itemId,
            @Valid @RequestBody AssignWorkshopRecipeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        var event = service.assignWorkshopRecipe(new AssignWorkshopRecipeCommand(
                itemId, request.recipeId(), request.actorId(), request.occurredAt(), correlationId, idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("event", event));
    }

    @GetMapping("/activity")
    Map<String, Object> activity(@RequestParam(required = false) String productId) {
        return Map.of("events", service.listActivity(productId));
    }

    @PostMapping("/shopping/items/{itemId}/status")
    ResponseEntity<Map<String, Object>> setShoppingItemStatus(
            @PathVariable String itemId,
            @Valid @RequestBody SetShoppingItemStatusRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        var event = service.setShoppingItemStatus(new SetShoppingItemStatusCommand(
                itemId, request.checked(), request.actorId(), request.occurredAt(), correlationId, idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("event", event));
    }

    @PostMapping("/projections/rebuild")
    Map<String, Object> rebuildProjections() {
        return Map.of("projection", service.rebuildProjections());
    }

    @GetMapping("/exports/{format}")
    ResponseEntity<String> export(@PathVariable String format) {
        var mediaType = "csv".equals(format) ? "text/csv" : "application/yaml";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mediaType + "; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"paints." + format + "\"")
                .body(service.exportPaints(format));
    }

    record AddWorkshopItemRequest(String itemId, @NotBlank String catalogItemId, @NotBlank String paintingProjectId, @NotBlank String displayName, String actorId, Instant occurredAt) {}
    record CreatePaintingProjectRequest(
            @NotBlank String paintableProductId,
            String paintingProjectId,
            String name,
            String actorId,
            Instant occurredAt) {}
    record ReplaceWorkshopPaintInventoryRequest(
            @JsonProperty("schema_version") int schemaVersion,
            @NotBlank String kind,
            @Valid List<WorkshopPaintEntryRequest> paints) {}
    record WorkshopPaintEntryRequest(
            @JsonProperty("paint_id") @NotBlank String paintId,
            int quantity) {}
    record TransitionStageRequest(@NotBlank String stage, @NotBlank String action, String comment, String reason, String actorId, Instant occurredAt) {}
    record AddWorkshopItemCommentRequest(@NotBlank String comment, String actorId, Instant occurredAt) {}
    record CreateWorkshopRecipeRequest(
            @JsonProperty("recipe_id") String recipeId,
            @JsonProperty("catalog_item_id") @NotBlank String catalogItemId,
            @JsonProperty("based_on_guide_id") String basedOnGuideId,
            @JsonProperty("supersedes_recipe_id") String supersedesRecipeId,
            @JsonProperty("display_name") @NotBlank String displayName,
            int version,
            List<Map<String, Object>> solutions,
            @JsonProperty("actor_id") String actorId,
            @JsonProperty("occurred_at") Instant occurredAt) {
        CreateWorkshopRecipeRequest {
            solutions = solutions == null ? List.of() : List.copyOf(solutions);
        }
    }
    record TransitionWorkshopRecipeRequest(@NotBlank String action, String comment, @JsonProperty("actor_id") String actorId, @JsonProperty("occurred_at") Instant occurredAt) {}
    record AssignWorkshopRecipeRequest(@JsonProperty("recipe_id") @NotBlank String recipeId, @JsonProperty("actor_id") String actorId, @JsonProperty("occurred_at") Instant occurredAt) {}
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
}
