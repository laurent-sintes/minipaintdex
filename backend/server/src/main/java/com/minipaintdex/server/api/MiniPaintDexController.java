package com.minipaintdex.server.api;

import com.minipaintdex.application.MiniPaintDexService;
import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.TransitionStageCommand;
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
            @RequestParam(required = false) String color) {
        var filters = new LinkedHashMap<String, String>();
        if (query != null) filters.put("query", query);
        if (brand != null) filters.put("brand", brand);
        if (range != null) filters.put("range", range);
        if (type != null) filters.put("type", type);
        if (color != null) filters.put("color", color);
        return Map.of("paints", service.searchMarketPaints(filters));
    }

    @GetMapping("/market/paints/{paintId}")
    Map<String, Object> paint(@PathVariable String paintId) {
        return Map.of("paint", service.getMarketPaint(paintId));
    }

    @GetMapping("/market/games")
    Map<String, Object> games() {
        return Map.of("games", service.listProjects());
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
}
