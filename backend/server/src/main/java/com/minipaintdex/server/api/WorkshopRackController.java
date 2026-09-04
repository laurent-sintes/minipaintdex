package com.minipaintdex.server.api;
import com.minipaintdex.application.usecase.WorkshopUseCases;
import com.minipaintdex.application.storage.StorageContracts.*;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.domain.workshop.storage.*;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/workshop")
final class WorkshopRackController {
    private final WorkshopUseCases workshop;
    WorkshopRackController(WorkshopUseCases workshop) { this.workshop = workshop; }
    @GetMapping("/racks")
    EntityModel<PageResult<RackView>> listWorkshopRacks(@ParameterObject Pageable pageable, @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        var result = workshop.listWorkshopRacks(new ListRacks(RackApiSupport.page(pageable), RackApiSupport.correlation(correlation)));
        return RackApiSupport.pages(result, result);
    }
    @GetMapping("/racks/{id}")
    EntityModel<RackDetail> getWorkshopRack(@PathVariable String id, @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        return EntityModel.of(workshop.getWorkshopRack(new GetRack(id, RackApiSupport.correlation(correlation))),
                Link.of("/api/v1/workshop/racks/" + id).withSelfRel(), Link.of("/api/v1/workshop/racks").withRel("collection"),
                Link.of("/api/v1/workshop/paint-storage/proposals").withRel("propose"));
    }
    @GetMapping("/paint-storage/pots")
    EntityModel<PageResult<PotView>> searchStoragePots(@ParameterObject Pageable pageable, @RequestParam(required=false) String query,
            @RequestParam(defaultValue="false") boolean unplacedOnly, @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        var result = workshop.searchStoragePots(new SearchPots(RackApiSupport.page(pageable), query, unplacedOnly, RackApiSupport.correlation(correlation)));
        return RackApiSupport.pages(result, result);
    }
    @PostMapping("/racks")
    ResponseEntity<PublicationResponse> saveWorkshopRack(@Valid @RequestBody RackRequest request,
            @RequestHeader("Idempotency-Key") String key, @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        return ApiResponses.accepted(workshop.saveWorkshopRack(new SaveRack(request.workshopRackId(), request.configuration(), request.expectedVersion(),
                request.removePlacements(), RackApiSupport.correlation(correlation), key)));
    }
    @PostMapping("/rack-acquisitions")
    ResponseEntity<PublicationResponse> addWorkshopRacks(@Valid @RequestBody AcquisitionRequest request,
            @RequestHeader("Idempotency-Key") String key, @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        return ApiResponses.accepted(workshop.addWorkshopRacks(new AddRacks(request.rackProductId(), request.quantity(), request.location(),
                RackApiSupport.correlation(correlation), key)));
    }
    @PostMapping("/paint-storage/container-identifications")
    ResponseEntity<PublicationResponse> identifyPaintPotContainer(@Valid @RequestBody ContainerRequest request,
            @RequestHeader("Idempotency-Key") String key, @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        return ApiResponses.accepted(workshop.identifyPaintPotContainer(new IdentifyContainer(request.paintPotId(), request.identification(), request.expectedVersion(),
                request.removePlacement(), RackApiSupport.correlation(correlation), key)));
    }
    @PostMapping("/paint-storage/proposals")
    Proposal previewPaintStorage(@RequestBody PreviewRequest request, @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        return workshop.previewPaintStorage(new Preview(request.paintPotIds(), request.workshopRackIds(), request.allOwnedPots(), request.mode(),
                request.allowEstimates(), request.preserveExisting(), RackApiSupport.correlation(correlation)));
    }
    @PostMapping("/paint-storage/confirmations")
    ResponseEntity<PublicationResponse> confirmPaintStorage(@Valid @RequestBody ConfirmRequest request,
            @RequestHeader("Idempotency-Key") String key, @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        return ApiResponses.accepted(workshop.confirmPaintStorage(new Confirm(request.snapshotToken(), request.placements(),
                request.allowEstimates(), RackApiSupport.correlation(correlation), key)));
    }
    @PostMapping("/paint-storage/placements")
    ResponseEntity<PublicationResponse> setPaintPotPlacement(@Valid @RequestBody PlacementRequest request,
            @RequestHeader("Idempotency-Key") String key, @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        return ApiResponses.accepted(workshop.setPaintPotPlacement(new SetPlacement(request.paintPotId(), request.placement(), request.snapshotToken(),
                request.allowEstimates(), RackApiSupport.correlation(correlation), key)));
    }
    record RackRequest(@NotBlank String workshopRackId, @NotNull WorkshopRack.Configuration configuration, @Min(0) long expectedVersion, boolean removePlacements) {}
    record AcquisitionRequest(@NotBlank String rackProductId, @Min(1) @Max(100) int quantity, String location) {}
    record ContainerRequest(@NotBlank String paintPotId, @NotNull PaintContainerIdentification identification, @Min(1) long expectedVersion, boolean removePlacement) {}
    record PreviewRequest(Set<String> paintPotIds, Set<String> workshopRackIds, boolean allOwnedPots, String mode, boolean allowEstimates, boolean preserveExisting) {}
    record ConfirmRequest(@NotBlank String snapshotToken, @NotNull List<PaintPotPlacement> placements, boolean allowEstimates) {}
    record PlacementRequest(@NotBlank String paintPotId, PaintPotPlacement placement, @NotBlank String snapshotToken, boolean allowEstimates) {}
}
