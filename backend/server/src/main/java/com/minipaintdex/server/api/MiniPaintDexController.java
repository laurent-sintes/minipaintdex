package com.minipaintdex.server.api;

import com.minipaintdex.application.MiniPaintDexService;
import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.ApplyMarketPaintChangeSetCommand;
import com.minipaintdex.application.command.ApplyMiniatureProjectChangeSetCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.TransitionStageCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        return Map.of("status", "ok", "service", "minipaintdex", "storage", "files");
    }

    @GetMapping("/bootstrap")
    Map<String, Object> bootstrap() {
        return service.bootstrap();
    }

    @GetMapping("/site/config")
    Object siteConfig() {
        return service.bootstrap().get("config");
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
            @RequestParam(required = false) String tag) {
        return Map.of("paints", service.searchMarketPaints(new SearchMarketPaintsQuery(
                query, brand, range, type, color, finish, medium, opacity, volume, reference, lifecycle, manufacturer, tag)));
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

    @PostMapping("/market/project-changesets")
    Map<String, Object> applyProjectChangeSet(
            @Valid @RequestBody ApplyProjectChangeSetRequest request,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        var items = request.workshopItems().stream()
                .map(item -> new ApplyMiniatureProjectChangeSetCommand.WorkshopItem(
                        item.id(), item.catalogItemId(), item.projectId(), item.displayName()))
                .toList();
        var result = service.applyMiniatureProjectChangeSet(new ApplyMiniatureProjectChangeSetCommand(
                request.schemaVersion(), request.kind(), request.project(), request.paintingGuides(), items,
                dryRun, request.actorId(), request.correlationId()));
        return Map.of("result", result);
    }

    @GetMapping("/market/paints/{paintId}")
    Map<String, Object> paint(@PathVariable String paintId) {
        return Map.of("paint", service.getMarketPaint(paintId));
    }

    @GetMapping("/market/games")
    Map<String, Object> games() {
        return Map.of("games", service.listProjects());
    }

    @GetMapping("/market/painting-guides")
    Map<String, Object> paintingGuides(@RequestParam(required = false) String catalogItemId) {
        return Map.of("paintingGuides", service.listMarketPaintingGuides(catalogItemId));
    }

    @GetMapping("/market/painting-guides/{guideId}/reconciliation")
    Map<String, Object> reconcilePaintingGuide(@PathVariable String guideId) {
        return service.reconcileMarketPaintingGuide(guideId);
    }

    @GetMapping("/workshop/projects")
    Map<String, Object> projects() {
        return Map.of("projects", service.listProjects());
    }

    @GetMapping("/workshop/items")
    Map<String, Object> workshopItems(@RequestParam(required = false) String projectId) {
        return Map.of("items", service.listWorkshopItems(projectId));
    }

    @PostMapping("/workshop/items")
    ResponseEntity<Map<String, Object>> addWorkshopItem(
            @Valid @RequestBody AddWorkshopItemRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        var event = service.addWorkshopItem(new AddWorkshopItemCommand(
                request.itemId(), request.catalogItemId(), request.projectId(), request.displayName(),
                request.actorId(), request.occurredAt(), correlationId, idempotencyKey));
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
    Map<String, Object> activity(@RequestParam(required = false) String projectId) {
        return Map.of("events", service.listActivity(projectId));
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

    record AddWorkshopItemRequest(String itemId, @NotBlank String catalogItemId, @NotBlank String projectId, @NotBlank String displayName, String actorId, Instant occurredAt) {}
    record TransitionStageRequest(@NotBlank String stage, @NotBlank String action, String comment, String reason, String actorId, Instant occurredAt) {}
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
    record ApplyProjectChangeSetRequest(
            @JsonProperty("schema_version") int schemaVersion,
            @NotBlank String kind,
            Map<String, Object> project,
            @JsonProperty("painting_guides") List<Map<String, Object>> paintingGuides,
            @JsonProperty("workshop_items") List<ProjectWorkshopItemRequest> workshopItems,
            @JsonProperty("actor_id") String actorId,
            @JsonProperty("correlation_id") String correlationId) {
        ApplyProjectChangeSetRequest {
            project = project == null ? Map.of() : Map.copyOf(project);
            paintingGuides = paintingGuides == null ? List.of() : List.copyOf(paintingGuides);
            workshopItems = workshopItems == null ? List.of() : List.copyOf(workshopItems);
        }
    }
    record ProjectWorkshopItemRequest(
            String id,
            @JsonProperty("catalog_item_id") String catalogItemId,
            @JsonProperty("project_id") String projectId,
            @JsonProperty("display_name") String displayName) {}
}
