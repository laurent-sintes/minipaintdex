package com.minipaintdex.application.command;
import com.minipaintdex.domain.market.storage.PaintContainerFormat;
import com.minipaintdex.domain.market.storage.RackProduct;
import com.minipaintdex.domain.shared.storage.StorageFields;
public record SaveRackReferenceCommand(PaintContainerFormat containerFormat, RackProduct rackProduct,
        long expectedRevision, String correlationId, boolean dryRun) {
    public SaveRackReferenceCommand {
        if ((containerFormat == null) == (rackProduct == null)) throw StorageFields.invalid("Supply exactly one catalog entry.");
        if (expectedRevision < 0) throw StorageFields.invalid("Invalid expected revision.");
        correlationId = StorageFields.text(correlationId, "correlationId");
    }
}
