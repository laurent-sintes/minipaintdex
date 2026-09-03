package com.minipaintdex.application.view;

import com.minipaintdex.domain.event.EventEnvelope;

import java.time.Instant;
import java.util.List;

/** Physical miniature or scenery item tracked independently in a painting project. */
public record WorkshopPaintableView(
        String id,
        String paintableComponentId,
        String paintingProjectId,
        String displayName,
        WorkflowView workflow,
        String currentStage,
        boolean completed,
        String recipeId,
        int recipeVersion,
        Instant updatedAt) {

    public record WorkflowView(
            String preparation, String priming, String pre_highlight,
            String painting, String finishing, String basing) {}

    /** Detail projection keeps the same item fields and its newest-first journal. */
    public record Detail(WorkshopPaintableView paintable, List<EventEnvelope> activity) {
        public Detail { activity = List.copyOf(activity); }
    }
}
