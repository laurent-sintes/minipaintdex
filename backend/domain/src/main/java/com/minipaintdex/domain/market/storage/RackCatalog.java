package com.minipaintdex.domain.market.storage;
import com.minipaintdex.domain.shared.storage.*;

import java.util.List;

public record RackCatalog(int schemaVersion, long revision, List<PaintContainerFormat> containerFormats, List<RackProduct> rackProducts) {
    public RackCatalog {
        if (schemaVersion != 1 || revision < 0) throw StorageFields.invalid("Invalid rack catalog version.");
        containerFormats = List.copyOf(containerFormats); rackProducts = List.copyOf(rackProducts);
        if (containerFormats.stream().map(PaintContainerFormat::id).distinct().count() != containerFormats.size()
                || rackProducts.stream().map(RackProduct::id).distinct().count() != rackProducts.size()) throw StorageFields.invalid("Duplicate catalog identity.");
        var formatIds = containerFormats.stream().map(PaintContainerFormat::id).toList();
        rackProducts.forEach(rack -> rack.rows().forEach(row -> row.slots().forEach(slot -> {
            if (!formatIds.containsAll(slot.acceptedFormatIds())) throw StorageFields.invalid("Unknown slot container format.");
        })));
        rackProducts.forEach(rack -> rack.rows().forEach(row -> row.capacityCalibrations().forEach(calibration -> {
            if (!formatIds.containsAll(calibration.containerFormatIds())) throw StorageFields.invalid("Unknown calibrated container format.");
        })));
    }
    public static RackCatalog empty() { return new RackCatalog(1, 0, List.of(), List.of()); }
    public void validateReplacement(RackCatalog next) {
        if (next.revision() != revision + 1) throw StorageFields.invalid("A replacement must increment the catalog revision once.");
        var formatIds = next.containerFormats().stream().map(PaintContainerFormat::id).toList();
        var rackIds = next.rackProducts().stream().map(RackProduct::id).toList();
        if (!formatIds.containsAll(containerFormats.stream().map(PaintContainerFormat::id).toList())
                || !rackIds.containsAll(rackProducts.stream().map(RackProduct::id).toList()))
            throw StorageFields.invalid("Catalog identities cannot be removed while workshop histories may reference them.");
    }
}
