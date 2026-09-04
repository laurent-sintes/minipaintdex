package com.minipaintdex.application.storage;

import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.domain.workshop.storage.*;
import com.minipaintdex.domain.shared.storage.RackRowDefinition;
import java.util.List;
import java.util.Set;

public final class StorageContracts {
    private StorageContracts() {}
    public record ListRacks(PageQuery page, String correlationId) {}
    public record GetRack(String workshopRackId, String correlationId) {}
    public record SearchPots(PageQuery page, String query, boolean unplacedOnly, String correlationId) {}
    public record AddRacks(String rackProductId, int quantity, String location, String correlationId, String idempotencyKey) {}
    public record SaveRack(String workshopRackId, WorkshopRack.Configuration configuration, long expectedVersion,
            boolean removePlacements, String correlationId, String idempotencyKey) {}
    public record IdentifyContainer(String paintPotId, PaintContainerIdentification identification, long expectedVersion,
            boolean removePlacement, String correlationId, String idempotencyKey) {}
    public record Preview(Set<String> paintPotIds, Set<String> workshopRackIds, boolean allOwnedPots,
            String mode, boolean allowEstimates, boolean preserveExisting, String correlationId) {
        public Preview {
            paintPotIds = paintPotIds == null ? Set.of() : Set.copyOf(paintPotIds);
            workshopRackIds = workshopRackIds == null ? Set.of() : Set.copyOf(workshopRackIds);
        }
    }
    public record Confirm(String snapshotToken, List<PaintPotPlacement> placements, boolean allowEstimates,
            String correlationId, String idempotencyKey) {
        public Confirm { placements = List.copyOf(placements); }
    }
    public record SetPlacement(String paintPotId, PaintPotPlacement placement, String snapshotToken,
            boolean allowEstimates, String correlationId, String idempotencyKey) {}
    public record RackView(String workshopRackId, long version, WorkshopRack.Configuration configuration,
            List<RackRowDefinition> rows, int placedPotCount) {
        public RackView { rows = List.copyOf(rows); }
    }
    public record PotView(String paintPotId, long version, String paintProductId, String name, String brand,
            String range, String colorHex, PaintContainerIdentification containerIdentification, PaintPotPlacement placement,
            StorageCompatibility.Assessment compatibility) {}
    public record RackDetail(RackView rack, List<PotView> pots, String snapshotToken, String correlationId) {
        public RackDetail { pots = List.copyOf(pots); }
    }
    public record Proposal(String snapshotToken, PaintStorageOrganizer.Proposal arrangement,
            List<PotView> pots, String correlationId) {
        public Proposal { pots = List.copyOf(pots); }
    }
}
