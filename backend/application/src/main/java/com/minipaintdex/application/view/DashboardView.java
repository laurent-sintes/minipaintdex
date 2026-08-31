package com.minipaintdex.application.view;

/** Lightweight home-page counters; it deliberately excludes catalog collections. */
public record DashboardView(
        PaintStats paintStats,
        int paintableProductCount,
        WorkshopStats workshop) {
    public record PaintStats(int total, int owned, long brands) {}
    public record WorkshopStats(int projectCount, int itemCount, long completedItemCount, int progressPercentage) {}
}
