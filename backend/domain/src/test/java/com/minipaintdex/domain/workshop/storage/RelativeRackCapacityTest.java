package com.minipaintdex.domain.workshop.storage;

import com.minipaintdex.domain.shared.storage.*;
import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class RelativeRackCapacityTest {
    private final PaintStoragePolicy policy = new PaintStoragePolicy(2, 500);
    private final RackRowDefinition row = new RackRowDefinition("row-1", "Row 1", "continuous", null, null,
            null, false, "unknown", List.of(), List.of(
            new RackRowDefinition.CapacityCalibration(List.of("standard"), 14, true, "Owner observation"),
            new RackRowDefinition.CapacityCalibration(List.of("citadel"), 11, true, "Owner observation")));
    private final List<StorageCompatibility.Rack> racks = List.of(new StorageCompatibility.Rack("rack-one", true, List.of(row)));
    private StorageCompatibility.Pot pot(String id, String format) {
        return new StorageCompatibility.Pot(id, format, ContainerDimensions.unknown(), "unknown", true,
                "paint-red", "brand", "range", "red", "base", id);
    }
    private PaintStorageOrganizer.Proposal propose(List<StorageCompatibility.Pot> pots, boolean allowEstimates) {
        return new PaintStorageOrganizer(policy).propose(pots, racks, List.of(),
                pots.stream().map(StorageCompatibility.Pot::paintPotId).collect(java.util.stream.Collectors.toSet()),
                Set.of("rack-one"), "brand-range", allowEstimates, false);
    }
    @Test void fullRowsFitExactlyAndOverflowRemainsUnplaced() {
        for (var entry : Map.of("standard", 14, "citadel", 11).entrySet()) {
            var pots = IntStream.range(0, entry.getValue() + 1).mapToObj(i -> pot("pot-" + i, entry.getKey())).toList();
            var result = propose(pots, true);
            assertEquals(entry.getValue(), result.placements().size());
            assertEquals(1, result.unplaced().size());
            assertTrue(result.placements().stream().allMatch(value -> value.offsetMm() == null && value.offsetFraction() != null));
        }
    }
    @Test void aFullInventoryRemainsBoundedAndDoesNotOverfill() {
        var pots = IntStream.range(0, 500).mapToObj(i -> pot("pot-" + i, "standard")).toList();
        var result = assertTimeout(java.time.Duration.ofSeconds(5), () -> propose(pots, true));
        assertEquals(14, result.placements().size());
        assertEquals(486, result.unplaced().size());
    }
    @Test void sevenStandardsAndFiveCitadelsFitButOneMoreCitadelDoesNot() {
        var pots = new ArrayList<StorageCompatibility.Pot>();
        for (int i = 0; i < 7; i++) pots.add(pot("a-standard-" + i, "standard"));
        for (int i = 0; i < 6; i++) pots.add(pot("z-citadel-" + i, "citadel"));
        var result = propose(pots, true);
        assertEquals(12, result.placements().size());
        assertEquals(1, result.unplaced().size());
        assertEquals("relative-capacity-mixed-fit-estimate", StorageCompatibility.assessRelative(pots.getFirst(), row).reason());
        assertEquals(0, propose(pots, false).placements().size());
    }
    @Test void missingFormatAndKnownExcessHeightAreNotMadeCompatibleByCalibration() {
        assertEquals("unknown", StorageCompatibility.assessRelative(pot("other", "unknown-format"), row).status());
        var limited = new RackRowDefinition("limited", "Limited", "continuous", null, null, 40.0, false,
                "confirmed", List.of(), row.capacityCalibrations());
        var tall = new StorageCompatibility.Pot("tall", "citadel", new ContainerDimensions(35.0, 33.0, 59.0),
                "confirmed", true, "paint-red", "brand", "range", "red", "base", "Tall");
        assertEquals("incompatible", StorageCompatibility.assessRelative(tall, limited).status());
    }
    @Test void overlappingAndMixedCoordinatePlacementsAreRejected() {
        var pots = List.of(pot("pot-a", "standard"), pot("pot-b", "citadel"));
        assertThrows(DomainException.class, () -> StorageCompatibility.validate(List.of(
                new PaintPotPlacement("pot-a", "rack-one", "row-1", null, null, false, 0.0),
                new PaintPotPlacement("pot-b", "rack-one", "row-1", null, null, false, 0.05)), pots, racks, policy, true));
        assertThrows(DomainException.class, () -> StorageCompatibility.validate(List.of(
                new PaintPotPlacement("pot-a", "rack-one", "row-1", null, null, false, 0.0),
                new PaintPotPlacement("pot-b", "rack-one", "row-1", 20.0, null, false)), pots, racks, policy, true));
    }
}
