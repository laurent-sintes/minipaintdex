package com.minipaintdex.domain.workshop.storage;

import com.minipaintdex.domain.shared.storage.StorageFields;

public record PaintPotPlacement(String paintPotId, String workshopRackId, String rackRowId,
        Double offsetMm, String slotId, boolean locked, Double offsetFraction) {
    public PaintPotPlacement(String paintPotId, String workshopRackId, String rackRowId, Double offsetMm, String slotId, boolean locked) {
        this(paintPotId, workshopRackId, rackRowId, offsetMm, slotId, locked, null);
    }
    public PaintPotPlacement {
        paintPotId = StorageFields.id(paintPotId); workshopRackId = StorageFields.id(workshopRackId); rackRowId = StorageFields.id(rackRowId);
        if ((offsetMm == null ? 0 : 1) + (slotId == null ? 0 : 1) + (offsetFraction == null ? 0 : 1) != 1)
            throw StorageFields.invalid("Select a millimetre offset, relative offset, or fixed slot.");
        if (offsetFraction != null && (!Double.isFinite(offsetFraction) || offsetFraction < 0 || offsetFraction >= 1))
            throw StorageFields.invalid("Relative offset must be between zero (inclusive) and one (exclusive).");
        if (offsetMm != null && (!Double.isFinite(offsetMm) || offsetMm < 0)) throw StorageFields.invalid("Offset must be finite and nonnegative.");
        if (slotId != null) slotId = StorageFields.id(slotId);
    }
}
