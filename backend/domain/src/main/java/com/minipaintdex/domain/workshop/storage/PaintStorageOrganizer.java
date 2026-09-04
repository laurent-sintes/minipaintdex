package com.minipaintdex.domain.workshop.storage;

import com.minipaintdex.domain.shared.storage.StorageFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class PaintStorageOrganizer {
    private final PaintStoragePolicy policy;
    public PaintStorageOrganizer(PaintStoragePolicy policy) { this.policy = policy; }
    public record Unplaced(String paintPotId, String reason) {}
    public record Proposal(List<PaintPotPlacement> placements, List<Unplaced> unplaced, int movedCount, int displacedCount) {
        public Proposal { placements = List.copyOf(placements); unplaced = List.copyOf(unplaced); }
    }
    public Proposal propose(List<StorageCompatibility.Pot> pots, List<StorageCompatibility.Rack> racks,
            List<PaintPotPlacement> existing, Set<String> selectedPotIds, Set<String> selectedRackIds,
            String mode, boolean allowEstimates, boolean preserveExisting) {
        if (!List.of("brand-range", "usage", "color").contains(mode)) throw StorageFields.invalid("Unknown organization mode.");
        if (selectedPotIds.size() > policy.maximumProposalPots()) throw StorageFields.invalid("Too many pots in one proposal.");
        if (!pots.stream().map(StorageCompatibility.Pot::paintPotId).toList().containsAll(selectedPotIds)
                || !racks.stream().map(StorageCompatibility.Rack::workshopRackId).toList().containsAll(selectedRackIds)) throw StorageFields.invalid("Unknown selection.");
        var destinations = racks.stream().filter(rack -> rack.owned() && selectedRackIds.contains(rack.workshopRackId()))
                .sorted(Comparator.comparing(StorageCompatibility.Rack::workshopRackId)).toList();
        var result = new ArrayList<>(existing.stream().filter(placement -> placement.locked()
                || !selectedPotIds.contains(placement.paintPotId()) || preserveExisting).toList());
        StorageCompatibility.validate(result, pots, racks, policy, allowEstimates);
        var unplaced = new ArrayList<Unplaced>();
        var selected = pots.stream().filter(pot -> selectedPotIds.contains(pot.paintPotId()))
                .sorted(Comparator.comparingLong((StorageCompatibility.Pot pot) -> compatibleCount(pot, destinations, allowEstimates))
                        .thenComparing(pot -> group(pot, mode)).thenComparing(StorageCompatibility.Pot::name)
                        .thenComparing(StorageCompatibility.Pot::paintPotId)).toList();
        for (var pot : selected) {
            if (result.stream().anyMatch(value -> value.paintPotId().equals(pot.paintPotId()))) continue;
            var placement = find(pot, pots, destinations, racks, result, allowEstimates);
            if (placement == null) {
                var reason = !pot.owned() ? "pot-not-owned"
                        : compatibleCount(pot, destinations, allowEstimates) > 0 ? "insufficient-capacity"
                        : !pot.dimensions().complete() ? "missing-container-dimensions-or-calibration" : "no-compatible-row";
                unplaced.add(new Unplaced(pot.paintPotId(), reason));
            } else result.add(placement);
        }
        var moved = (int) result.stream().filter(placement -> existing.stream().noneMatch(old -> old.equals(placement))).count();
        int displaced = (int) existing.stream().filter(old -> result.stream().noneMatch(value -> value.paintPotId().equals(old.paintPotId()))).count();
        return new Proposal(result, unplaced, moved, displaced);
    }
    private long compatibleCount(StorageCompatibility.Pot pot, List<StorageCompatibility.Rack> racks, boolean estimates) {
        return racks.stream().flatMap(rack -> rack.rows().stream()).filter(row -> "continuous".equals(row.support())
                ? (StorageCompatibility.assess(pot, row, null).permits(estimates) || StorageCompatibility.assessRelative(pot, row).permits(estimates))
                : row.slots().stream().anyMatch(slot -> StorageCompatibility.assess(pot, row, slot.id()).permits(estimates))).count();
    }
    private PaintPotPlacement find(StorageCompatibility.Pot pot, List<StorageCompatibility.Pot> pots,
            List<StorageCompatibility.Rack> destinations, List<StorageCompatibility.Rack> allRacks,
            List<PaintPotPlacement> placed, boolean estimates) {
        for (var rack : destinations) for (var row : rack.rows()) {
            var candidates = new ArrayList<PaintPotPlacement>();
            if ("fixed-slots".equals(row.support())) {
                for (var slot : row.slots()) if (StorageCompatibility.assess(pot, row, slot.id()).permits(estimates))
                    candidates.add(new PaintPotPlacement(pot.paintPotId(), rack.workshopRackId(), row.id(), null, slot.id(), false));
            } else if (StorageCompatibility.assessRelative(pot, row).permits(estimates)
                    && (row.widthMm() == null || placed.stream().anyMatch(value -> value.workshopRackId().equals(rack.workshopRackId())
                        && value.rackRowId().equals(row.id()) && value.offsetFraction() != null))) {
                candidates.add(new PaintPotPlacement(pot.paintPotId(), rack.workshopRackId(), row.id(), null, null, false, 0.0));
                for (var value : placed) if (value.workshopRackId().equals(rack.workshopRackId()) && value.rackRowId().equals(row.id()) && value.offsetFraction() != null) {
                    var occupied = pots.stream().filter(item -> item.paintPotId().equals(value.paintPotId())).findFirst().orElseThrow();
                    var fraction = row.occupiedFraction(occupied.containerFormatId());
                    if (fraction != null && value.offsetFraction() + fraction < 1.0 - 1e-9)
                        candidates.add(new PaintPotPlacement(pot.paintPotId(), rack.workshopRackId(), row.id(), null, null, false, value.offsetFraction() + fraction));
                }
            } else if (StorageCompatibility.assess(pot, row, null).permits(estimates)) {
                candidates.add(new PaintPotPlacement(pot.paintPotId(), rack.workshopRackId(), row.id(), 0.0, null, false));
                placed.stream().filter(value -> value.workshopRackId().equals(rack.workshopRackId()) && value.rackRowId().equals(row.id()))
                        .filter(value -> value.offsetMm() != null).sorted(Comparator.comparing(PaintPotPlacement::offsetMm)).forEach(value -> {
                            var occupied = pots.stream().filter(item -> item.paintPotId().equals(value.paintPotId())).findFirst().orElseThrow();
                            candidates.add(new PaintPotPlacement(pot.paintPotId(), rack.workshopRackId(), row.id(),
                                    value.offsetMm() + occupied.dimensions().widthMm() + policy.gapMm(), null, false));
                        });
            }
            for (var candidate : candidates) {
                var attempt = new ArrayList<>(placed); attempt.add(candidate);
                try { StorageCompatibility.validate(attempt, pots, allRacks, policy, estimates); return candidate; }
                catch (com.minipaintdex.domain.shared.DomainException ignored) { /* Try the next bounded, deterministic position. */ }
            }
        }
        return null;
    }
    private static String group(StorageCompatibility.Pot pot, String mode) {
        return switch (mode) {
            case "brand-range" -> pot.brand() + "|" + pot.range() + "|" + pot.colorFamily();
            case "usage" -> pot.usage() + "|" + pot.colorFamily();
            default -> pot.colorFamily() + "|" + pot.brand();
        };
    }
}
