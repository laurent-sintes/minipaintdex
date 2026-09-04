package com.minipaintdex.domain.workshop.storage;

import com.minipaintdex.domain.shared.storage.ContainerDimensions;
import com.minipaintdex.domain.shared.storage.StorageFields;

public record PaintContainerIdentification(String containerFormatId, ContainerDimensions measuredDimensions,
        String evidenceStatus, String note) {
    public PaintContainerIdentification {
        if (containerFormatId != null) containerFormatId = StorageFields.id(containerFormatId);
        if (containerFormatId == null && measuredDimensions == null) throw StorageFields.invalid("Identify a format or supply measurements.");
        evidenceStatus = StorageFields.evidence(evidenceStatus);
        note = StorageFields.text(note, "measurement/identification note");
        if ("confirmed".equals(evidenceStatus) && (measuredDimensions == null || !measuredDimensions.complete()))
            throw StorageFields.invalid("Confirmed personal geometry requires all three measured dimensions.");
    }
}
