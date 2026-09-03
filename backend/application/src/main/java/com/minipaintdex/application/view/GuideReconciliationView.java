package com.minipaintdex.application.view;

import java.util.List;

/** Ranked substitutions between a market painting guide and paints owned in the workshop. */
public record GuideReconciliationView(
        MarketPaintingGuideView guide,
        List<SlotReconciliationView> slots,
        int ownedPaintCount) {
    public GuideReconciliationView { slots = List.copyOf(slots); }

    public record SlotReconciliationView(
            MarketPaintingGuideView.SlotView slot,
            PaintProductView sourcePaint,
            List<PaintMatchView> candidates,
            boolean requiresManualReview) {
        public SlotReconciliationView { candidates = List.copyOf(candidates); }
    }

    public record PaintMatchView(
            PaintProductView paint,
            double score,
            double deltaE2000,
            boolean requiresManualReview,
            String strategy,
            DimensionsView dimensions,
            List<String> reasons) {
        public PaintMatchView { reasons = List.copyOf(reasons); }
    }

    public record DimensionsView(
            double color,
            double role,
            double behavior,
            double finish,
            double coverage,
            double medium) {}
}
