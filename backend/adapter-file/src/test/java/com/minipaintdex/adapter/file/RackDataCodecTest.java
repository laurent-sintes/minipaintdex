package com.minipaintdex.adapter.file;

import com.minipaintdex.domain.event.*;
import com.minipaintdex.domain.market.storage.*;
import com.minipaintdex.domain.shared.storage.*;
import com.minipaintdex.domain.workshop.PaintPotEvent.PaintPotContainerIdentified;
import com.minipaintdex.domain.workshop.storage.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RackDataCodecTest {
    private static final Instant AT = Instant.parse("2026-09-03T20:00:00Z");
    @Test void preservesRelativeCapacityHeightEvidenceAndEventHistory() {
        var row = new RackRowDefinition("row-1", "Row 1", "continuous", null, null, null, false,
                "unknown", List.of(), List.of(new RackRowDefinition.CapacityCalibration(List.of("dropper"), 14, true, "Owner observation")));
        var configuration = new WorkshopRack.Configuration(null, "Wooden rack", "Desk", true, List.of(row));
        var events = List.<DomainEvent>of(
                new WorkshopRack.Registered("wooden-rack", configuration, AT),
                new WorkshopRack.Configured("wooden-rack", configuration, AT),
                new PaintPotContainerIdentified("pot-one", new PaintContainerIdentification("dropper", null, "estimated", "Identified by owner"), AT),
                new WorkshopPaintStorage.ArrangementRecorded(WorkshopPaintStorage.ID, List.of(
                        new PaintPotPlacement("pot-one", "wooden-rack", "row-1", null, null, true, 0.5)), true, AT));
        var codec = new DomainEventCodec();
        for (int i = 0; i < events.size(); i++) {
            var envelope = new EventEnvelope("event-" + i, 1, 1, AT, new Actor("user", "owner"), "rack-test", null, "key-" + i, events.get(i));
            assertEquals(envelope, codec.decode(codec.encode(envelope)));
        }
        var rackCodec = new RackDataCodec();
        var format = new PaintContainerFormat(1, "dropper", "Dropper", "Unknown", "dropper", null,
                ContainerDimensions.unknown(), "unknown", List.of(), "No dimensions asserted");
        var product = new RackProduct(1, "test-rack", "Test rack", "Test", "test", "desktop", "tiered", List.of(row),
                List.of("https://example.org/rack"), "Test fixture only", List.of());
        var catalog = new RackCatalog(1, 1, List.of(format), List.of(product));
        assertEquals(catalog, rackCodec.catalog(rackCodec.encode(catalog)));
    }
}
