package com.minipaintdex.application.storage;

import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.validation.MarketCatalogFactory;
import com.minipaintdex.domain.workshop.*;
import com.minipaintdex.domain.workshop.storage.*;
import com.minipaintdex.domain.shared.storage.*;
import com.minipaintdex.domain.shared.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

public final class StorageProjection {
    private StorageProjection() {}
    public static WorkshopPaintStorage storage(DataSnapshot snapshot) {
        return WorkshopPaintStorage.rehydrate(snapshot.events().stream().map(value -> value.event())
                .filter(WorkshopPaintStorage.ArrangementRecorded.class::isInstance)
                .map(WorkshopPaintStorage.ArrangementRecorded.class::cast).toList());
    }
    public static List<WorkshopRack> racks(DataSnapshot snapshot) {
        var grouped = snapshot.events().stream().map(value -> value.event()).filter(WorkshopRack.RackEvent.class::isInstance)
                .map(WorkshopRack.RackEvent.class::cast).collect(Collectors.groupingBy(WorkshopRack.RackEvent::workshopRackId, LinkedHashMap::new, Collectors.toList()));
        return grouped.values().stream().map(WorkshopRack::rehydrate).sorted(Comparator.comparing(WorkshopRack::id)).toList();
    }
    public static List<RackRowDefinition> rows(WorkshopRack.Configuration configuration, DataSnapshot snapshot) {
        if (configuration.rackProductId() == null) return configuration.rowOverrides();
        var reference = snapshot.rackCatalog().rackProducts().stream().filter(value -> value.id().equals(configuration.rackProductId()))
                .findFirst().orElseThrow(() -> new DomainException("not_found", "Rack product not found."));
        return configuration.rowOverrides().isEmpty() ? reference.rows() : configuration.rowOverrides();
    }
    public static void validateConfiguration(WorkshopRack.Configuration configuration, DataSnapshot snapshot) {
        var formats = snapshot.rackCatalog().containerFormats().stream().map(value -> value.id()).collect(Collectors.toSet());
        for (var row : rows(configuration, snapshot)) {
            if (row.slots().stream().anyMatch(slot -> !formats.containsAll(slot.acceptedFormatIds()))
                    || row.capacityCalibrations().stream().anyMatch(value -> !formats.containsAll(value.containerFormatIds())))
                throw new DomainException("invalid_input", "Unknown container format in rack configuration.");
        }
    }
    public static Context context(DataSnapshot snapshot) {
        var storage = storage(snapshot);
        var rackAggregates = racks(snapshot);
        var rackShapes = rackAggregates.stream().map(rack -> new StorageCompatibility.Rack(rack.id(), rack.configuration().owned(), rows(rack.configuration(), snapshot))).toList();
        var products = MarketCatalogFactory.create(snapshot.paintProducts(), snapshot.paintableProducts(), snapshot.marketPaintingGuides(),
                snapshot.paintCatalogEditions(), snapshot.paintUsageGuides()).paints().stream().collect(Collectors.toMap(value -> value.id(), value -> value));
        var potAggregates = PaintPotProjector.project(snapshot.events());
        var pots = new ArrayList<StorageCompatibility.Pot>();
        var views = new ArrayList<StorageContracts.PotView>();
        for (var pot : potAggregates) {
            var product = products.get(pot.paintProductId());
            if (product == null) throw new DomainException("not_found", "Paint product not found.");
            var identification = pot.containerIdentification();
            var formatId = identification != null && identification.containerFormatId() != null
                    ? identification.containerFormatId() : product.containerFormatId();
            var format = snapshot.rackCatalog().containerFormats().stream().filter(value -> value.id().equals(formatId)).findFirst().orElse(null);
            var dimensions = identification != null && identification.measuredDimensions() != null ? identification.measuredDimensions()
                    : format != null ? format.dimensions() : ContainerDimensions.unknown();
            var evidence = identification != null && identification.measuredDimensions() != null ? identification.evidenceStatus()
                    : format != null ? format.evidenceStatus() : "unknown";
            var shape = new StorageCompatibility.Pot(pot.id(), formatId, dimensions, evidence, pot.possession() == PaintPotPossession.OWNED,
                    product.id(), product.brand(), product.range(), Objects.toString(product.color().family(), ""),
                    String.join(",", product.profile().roleIds()), product.name());
            pots.add(shape);
            var placement = storage.placements().stream().filter(value -> value.paintPotId().equals(pot.id())).findFirst().orElse(null);
            StorageCompatibility.Assessment assessment = null;
            if (placement != null) {
                var rack = rackShapes.stream().filter(value -> value.workshopRackId().equals(placement.workshopRackId())).findFirst().orElse(null);
                var row = rack == null ? null : rack.rows().stream().filter(value -> value.id().equals(placement.rackRowId())).findFirst().orElse(null);
                assessment = rack == null || !rack.owned() || row == null ? new StorageCompatibility.Assessment("incompatible", "missing-rack-or-row")
                        : placement.offsetFraction() != null ? StorageCompatibility.assessRelative(shape, row) : StorageCompatibility.assess(shape, row, placement.slotId());
            }
            views.add(new StorageContracts.PotView(pot.id(), pot.version(), product.id(), product.name(), product.brand(), product.range(),
                    product.color().hex(), identification, placement, assessment));
        }
        return new Context(snapshotToken(snapshot), storage, rackAggregates, rackShapes, pots, views);
    }
    public static String snapshotToken(DataSnapshot snapshot) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(snapshot.rackCatalog().toString().getBytes(StandardCharsets.UTF_8));
            for (var product : snapshot.paintProducts()) digest.update(product.toString().getBytes(StandardCharsets.UTF_8));
            for (var event : snapshot.events()) digest.update((event.eventId() + ":" + event.aggregateVersion() + ";").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public record Context(String token, WorkshopPaintStorage storage, List<WorkshopRack> rackAggregates,
            List<StorageCompatibility.Rack> racks, List<StorageCompatibility.Pot> pots, List<StorageContracts.PotView> potViews) {
        public StorageContracts.RackView rackView(WorkshopRack rack) {
            var shape = racks.stream().filter(value -> value.workshopRackId().equals(rack.id())).findFirst().orElseThrow();
            return new StorageContracts.RackView(rack.id(), rack.version(), rack.configuration(), shape.rows(),
                    (int) storage.placements().stream().filter(value -> value.workshopRackId().equals(rack.id())).count());
        }
    }
}
