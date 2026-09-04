package com.minipaintdex.domain.workshop.storage;

import com.minipaintdex.domain.shared.storage.*;
import java.util.List;

public final class StorageCompatibility {
    private StorageCompatibility() {}
    public record Pot(String paintPotId, String containerFormatId, ContainerDimensions dimensions, String evidenceStatus,
            boolean owned, String paintProductId, String brand, String range, String colorFamily, String usage, String name) {}
    public record Rack(String workshopRackId, boolean owned, List<RackRowDefinition> rows) {
        public Rack { rows = List.copyOf(rows); }
    }
    public record Assessment(String status, String reason) {
        public boolean permits(boolean allowEstimates) { return "compatible".equals(status) || (allowEstimates && "estimated".equals(status)); }
    }
    public static Assessment assess(Pot pot, RackRowDefinition row, String slotId) {
        if (!pot.owned()) return new Assessment("incompatible", "pot-not-owned");
        var dimensions = pot.dimensions();
        Double width = row.widthMm(), depth = row.depthMm();
        boolean documentedSlot = false;
        if ("fixed-slots".equals(row.support())) {
            var slot = row.slots().stream().filter(value -> value.id().equals(slotId)).findFirst().orElse(null);
            if (slot == null) return new Assessment("incompatible", "unknown-slot");
            width = slot.widthMm(); depth = slot.depthMm();
            if (!slot.acceptedFormatIds().isEmpty()) {
                if (pot.containerFormatId() == null || !slot.acceptedFormatIds().contains(pot.containerFormatId()))
                    return new Assessment("incompatible", "unsupported-slot-format");
                documentedSlot = true;
            }
        } else if (slotId != null) return new Assessment("incompatible", "continuous-row-has-no-slots");
        if ((width != null && dimensions.widthMm() != null && dimensions.widthMm() > width)
                || (depth != null && dimensions.depthMm() != null && dimensions.depthMm() > depth)
                || (!row.openTop() && row.clearanceHeightMm() != null && dimensions.heightMm() != null
                    && dimensions.heightMm() > row.clearanceHeightMm())) return new Assessment("incompatible", "container-too-large");
        if (!dimensions.complete() || (!documentedSlot && (width == null || depth == null))
                || (!row.openTop() && row.clearanceHeightMm() == null)
                || "unknown".equals(row.evidenceStatus()) || "unknown".equals(pot.evidenceStatus()))
            return new Assessment("unknown", "missing-dimensions-or-evidence");
        // A support-specific format list documents stability; dimensions alone do not prove a bottle is retained by a hole.
        if ("fixed-slots".equals(row.support()) && !documentedSlot) return new Assessment("unknown", "slot-retention-unverified");
        if (!"confirmed".equals(pot.evidenceStatus()) || !"confirmed".equals(row.evidenceStatus()))
            return new Assessment("estimated", "provisional-dimensions");
        return new Assessment("compatible", "dimensions-fit");
    }
    public static Assessment assessRelative(Pot pot, RackRowDefinition row) {
        if (!pot.owned()) return new Assessment("incompatible", "pot-not-owned");
        if (!"continuous".equals(row.support()) || row.occupiedFraction(pot.containerFormatId()) == null)
            return new Assessment("unknown", "missing-capacity-calibration");
        if ((!row.openTop() && row.clearanceHeightMm() != null && pot.dimensions().heightMm() != null
                && pot.dimensions().heightMm() > row.clearanceHeightMm())
                || (row.depthMm() != null && pot.dimensions().depthMm() != null && pot.dimensions().depthMm() > row.depthMm()))
            return new Assessment("incompatible", "container-too-large");
        boolean heightVerified = row.capacityCalibrations().stream().anyMatch(value -> value.heightVerified()
                && value.containerFormatIds().contains(pot.containerFormatId()));
        return new Assessment("estimated", heightVerified ? "relative-capacity-mixed-fit-estimate" : "relative-capacity-height-to-verify");
    }
    public static void validate(List<PaintPotPlacement> placements, List<Pot> pots, List<Rack> racks,
            PaintStoragePolicy policy, boolean allowEstimates) {
        var potById = pots.stream().collect(java.util.stream.Collectors.toMap(Pot::paintPotId, value -> value));
        var rackById = racks.stream().collect(java.util.stream.Collectors.toMap(Rack::workshopRackId, value -> value));
        var ids = new java.util.HashSet<String>();
        var groups = new java.util.HashMap<String, java.util.List<Interval>>();
        var groupCoordinates = new java.util.HashMap<String, Boolean>();
        var slots = new java.util.HashSet<String>();
        for (var placement : placements) {
            if (!ids.add(placement.paintPotId())) throw StorageFields.invalid("A pot cannot occupy two positions.");
            var pot = potById.get(placement.paintPotId());
            var rack = rackById.get(placement.workshopRackId());
            if (pot == null || rack == null) throw StorageFields.invalid("Unknown pot or rack.");
            if (!rack.owned()) throw StorageFields.invalid("Rack is no longer owned.");
            var row = rack.rows().stream().filter(value -> value.id().equals(placement.rackRowId())).findFirst()
                    .orElseThrow(() -> StorageFields.invalid("Unknown rack row."));
            boolean relative = placement.offsetFraction() != null;
            var assessment = relative ? assessRelative(pot, row) : assess(pot, row, placement.slotId());
            if (!assessment.permits(allowEstimates)) throw StorageFields.invalid(assessment.reason());
            String key = rack.workshopRackId() + "/" + row.id();
            if (placement.slotId() != null) {
                if (!slots.add(key + "/" + placement.slotId())) throw StorageFields.invalid("Slot is already occupied.");
                continue;
            }
            var previousCoordinates = groupCoordinates.putIfAbsent(key, relative);
            if (previousCoordinates != null && previousCoordinates != relative) throw StorageFields.invalid("Cannot mix relative and millimetre positions in one row.");
            double left = relative ? placement.offsetFraction() : placement.offsetMm();
            double right = left + (relative ? row.occupiedFraction(pot.containerFormatId()) : pot.dimensions().widthMm());
            double limit = relative ? 1.0 : row.widthMm();
            if (right > limit + 1e-9) throw StorageFields.invalid("Row capacity exceeded.");
            groups.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(new Interval(left, right));
        }
        for (var entry : groups.entrySet()) {
            var intervals = entry.getValue();
            intervals.sort(java.util.Comparator.comparingDouble(Interval::left));
            double gap = groupCoordinates.get(entry.getKey()) ? 0 : policy.gapMm();
            for (int i = 1; i < intervals.size(); i++) {
                if (intervals.get(i).left() < intervals.get(i - 1).right() + gap - 1e-9)
                    throw StorageFields.invalid("Pots overlap or handling gap is insufficient.");
            }
        }
    }
    private record Interval(double left, double right) {}
}
