package com.minipaintdex.application.view;

import com.minipaintdex.domain.event.EventEnvelope;

import java.util.List;

/** Global workshop projection derived from the ledger. */
public record WorkshopOverviewView(
        String id,
        List<PaintingProjectView> paintingProjects,
        int projectCount,
        int itemCount,
        int completedItemCount,
        int progressPercentage,
        List<EventEnvelope> recentActivity) {
    public WorkshopOverviewView {
        paintingProjects = List.copyOf(paintingProjects);
        recentActivity = List.copyOf(recentActivity);
    }
}
