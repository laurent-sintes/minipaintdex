package com.minipaintdex.domain.workshop.storage;

import com.minipaintdex.domain.shared.storage.StorageFields;

public record PaintStoragePolicy(double gapMm, int maximumProposalPots) {
    public PaintStoragePolicy {
        if (!Double.isFinite(gapMm) || gapMm < 0 || maximumProposalPots < 1) throw StorageFields.invalid("Invalid storage policy.");
    }
}
