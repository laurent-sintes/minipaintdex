package com.minipaintdex.application.view;

import java.time.Instant;
import java.util.List;

/** Read model of one workshop painting-project aggregate. */
public record PaintingProjectView(
        String paintingProjectId,
        String paintableProductId,
        String name,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant importedAt,
        int paintableCount,
        int completedCount,
        int inProgressCount,
        int pendingCount,
        int progressPercentage,
        int requiredPaintCount,
        int missingPaintCount,
        List<MissingPaintView> missingPaints,
        int pendingPaintSlotCount,
        boolean orphaned) {
    public PaintingProjectView { missingPaints = List.copyOf(missingPaints); }
}
