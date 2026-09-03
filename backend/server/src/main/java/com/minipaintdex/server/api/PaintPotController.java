package com.minipaintdex.server.api;

import com.minipaintdex.application.command.*;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchPaintPotsQuery;
import com.minipaintdex.application.result.ImportPaintPotsResult;
import com.minipaintdex.application.usecase.WorkshopUseCases;
import com.minipaintdex.application.view.PaintPotView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/workshop")
final class PaintPotController {
    private final WorkshopUseCases workshop;
    PaintPotController(WorkshopUseCases workshop) { this.workshop = workshop; }

    @GetMapping("/paint-pots")
    EntityModel<PaintPotPageResponse> searchPaintPots(@RequestParam(required = false) String paintProductId,
            @RequestParam(defaultValue = "false") boolean includeRemoved, @ParameterObject Pageable pageable) {
        if (pageable.getSort().isSorted()) throw new com.minipaintdex.domain.shared.DomainException("invalid_input", "Paint pots are ordered by stable identity.");
        var page = workshop.searchPaintPots(new SearchPaintPotsQuery(paintProductId, includeRemoved,
                new PageQuery(pageable.getPageNumber(), pageable.getPageSize(), List.of())));
        var model = EntityModel.of(new PaintPotPageResponse(page.content(), page.totalElements(), page.page(), page.size(), page.totalPages()));
        model.add(pageLink(page.page(), page.size()).withSelfRel(), pageLink(0, page.size()).withRel("first"),
                pageLink(Math.max(0, page.totalPages() - 1), page.size()).withRel("last"));
        if (page.hasPrevious()) model.add(pageLink(page.page() - 1, page.size()).withRel("previous"));
        if (page.hasNext()) model.add(pageLink(page.page() + 1, page.size()).withRel("next"));
        return model;
    }
    private Link pageLink(int page, int size) {
        return Link.of(ServletUriComponentsBuilder.fromCurrentRequest().replaceQueryParam("page", page).replaceQueryParam("size", size).build().toUriString());
    }
    @GetMapping("/paint-pots/{paintPotId}")
    EntityModel<PaintPotView> getPaintPot(@PathVariable String paintPotId) {
        var pot = workshop.getPaintPot(paintPotId);
        var base = "/api/v1/workshop/paint-pots/" + pot.paintPotId();
        var result = EntityModel.of(pot, Link.of(base).withSelfRel(), Link.of("/api/v1/workshop/paint-pots").withRel("collection"),
                Link.of("/api/v1/market/paint-products/" + pot.paintProductId()).withRel("paint-product"));
        for (var action : pot.allowedActions()) {
            var resource = switch (action) {
                case "observe" -> "observations";
                case "open" -> "openings";
                case "change-possession" -> "possession-changes";
                case "add-note" -> "notes";
                case "add-photo" -> "photos";
                default -> throw new IllegalStateException("Unknown pot action: " + action);
            };
            result.add(Link.of(base + "/" + resource).withRel(action));
        }
        return result;
    }
    @PostMapping("/paint-pots")
    ResponseEntity<ResultResponse<ImportPaintPotsResult>> registerPaintPot(@Valid @RequestBody RegisterPaintPotRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation) {
        return imported(workshop.registerPaintPot(new RegisterPaintPotCommand(request.paintPotId(), request.paintProductId(),
                request.acquiredAt(), request.actorId(), correlation, key)));
    }
    @PostMapping("/paint-pot-imports")
    ResponseEntity<ResultResponse<ImportPaintPotsResult>> importPaintPots(@Valid @RequestBody ImportPaintPotsRequest request,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation) {
        return imported(workshop.importPaintPots(new ImportPaintPotsCommand(request.schemaVersion(), request.kind(),
                request.pots().stream().map(value -> new ImportPaintPotsCommand.Registration(value.paintPotId(), value.paintProductId(), value.acquiredAt())).toList(),
                dryRun, request.actorId(), correlation, key)));
    }
    private ResponseEntity<ResultResponse<ImportPaintPotsResult>> imported(ImportPaintPotsResult result) {
        var body = new ResultResponse<>(result);
        return result.publication() == null ? ResponseEntity.ok(body)
                : ResponseEntity.accepted().location(URI.create("/api/v1/publications/" + result.publication().publicationId())).body(body);
    }
    @PostMapping("/paint-pots/{paintPotId}/observations")
    ResponseEntity<PublicationResponse> observePaintPot(@PathVariable String paintPotId, @Valid @RequestBody ObservePaintPotRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation) {
        return ApiResponses.accepted(workshop.observePaintPot(new ObservePaintPotCommand(paintPotId, request.condition(), request.remainingLevel(),
                request.actorId(), request.occurredAt(), correlation, key)));
    }
    @PostMapping("/paint-pots/{paintPotId}/openings")
    ResponseEntity<PublicationResponse> openPaintPot(@PathVariable String paintPotId, @Valid @RequestBody OpenPaintPotRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation) {
        return ApiResponses.accepted(workshop.openPaintPot(new OpenPaintPotCommand(paintPotId, request.actorId(), request.occurredAt(), correlation, key)));
    }
    @PostMapping("/paint-pots/{paintPotId}/possession-changes")
    ResponseEntity<PublicationResponse> changePaintPotPossession(@PathVariable String paintPotId, @Valid @RequestBody ChangePaintPotPossessionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation) {
        return ApiResponses.accepted(workshop.changePaintPotPossession(new ChangePaintPotPossessionCommand(paintPotId, request.possession(),
                request.actorId(), request.occurredAt(), correlation, key)));
    }
    @PostMapping("/paint-pots/{paintPotId}/notes")
    ResponseEntity<PublicationResponse> addPaintPotNote(@PathVariable String paintPotId, @Valid @RequestBody AddPaintPotNoteRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation) {
        return ApiResponses.accepted(workshop.addPaintPotNote(new AddPaintPotNoteCommand(paintPotId, request.note(), request.actorId(), request.occurredAt(), correlation, key)));
    }
    @PostMapping(value = "/paint-pots/{paintPotId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<PublicationResponse> addPaintPotPhoto(@PathVariable String paintPotId, @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String caption, @RequestParam(required = false) String actorId,
            @RequestParam(required = false) Instant occurredAt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlation) throws IOException {
        return ApiResponses.accepted(workshop.addPaintPotPhoto(new AddPaintPotPhotoCommand(paintPotId, file.getOriginalFilename(),
                file.getContentType(), file.getBytes(), caption, actorId, occurredAt, correlation, key)));
    }
}
record PaintPotPageResponse(List<PaintPotView> pots, long total, int page, int size, int totalPages) {}
record RegisterPaintPotRequest(@NotBlank String paintPotId, @NotBlank String paintProductId, Instant acquiredAt, String actorId) {}
record ImportPaintPotsRequest(int schemaVersion, @NotBlank String kind, @Valid List<RegisterPaintPotRequest> pots, String actorId) {
    ImportPaintPotsRequest { pots = pots == null ? List.of() : List.copyOf(pots); }
}
record ObservePaintPotRequest(@NotBlank String condition, @NotBlank String remainingLevel, String actorId, Instant occurredAt) {}
record OpenPaintPotRequest(String actorId, Instant occurredAt) {}
record ChangePaintPotPossessionRequest(@NotBlank String possession, String actorId, Instant occurredAt) {}
record AddPaintPotNoteRequest(@NotBlank String note, String actorId, Instant occurredAt) {}
