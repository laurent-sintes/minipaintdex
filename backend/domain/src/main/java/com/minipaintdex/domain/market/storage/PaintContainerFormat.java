package com.minipaintdex.domain.market.storage;
import com.minipaintdex.domain.shared.storage.*;

import java.util.List;

public record PaintContainerFormat(int schemaVersion, String id, String name, String brand,
        String family, Integer volumeMl, ContainerDimensions dimensions, String evidenceStatus,
        List<String> sources, String notes) {
    public PaintContainerFormat {
        if (schemaVersion != 1) throw StorageFields.invalid("schemaVersion must be 1.");
        id = StorageFields.id(id); name = StorageFields.text(name, "name");
        brand = StorageFields.text(brand, "brand"); family = StorageFields.id(family);
        if (volumeMl != null && volumeMl <= 0) throw StorageFields.invalid("Volume must be positive.");
        dimensions = dimensions == null ? ContainerDimensions.unknown() : dimensions;
        evidenceStatus = StorageFields.evidence(evidenceStatus); sources = StorageFields.sources(sources);
        if (!"unknown".equals(evidenceStatus) && sources.isEmpty()) throw StorageFields.invalid("Known formats require sources.");
        notes = notes == null ? "" : notes;
    }
}
