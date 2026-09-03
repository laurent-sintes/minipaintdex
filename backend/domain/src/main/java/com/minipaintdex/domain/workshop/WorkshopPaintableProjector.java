package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class WorkshopPaintableProjector {
    private WorkshopPaintableProjector() {}

    public static List<WorkshopPaintableState> project(List<? extends DomainEvent> events) {
        var histories = new LinkedHashMap<String, List<WorkshopPaintableEvent>>();
        for (var candidate : events) {
            var event = candidate instanceof EventEnvelope envelope ? envelope.event() : candidate;
            if (event instanceof WorkshopPaintableEvent itemEvent) {
                histories.computeIfAbsent(itemEvent.aggregateId(), ignored -> new ArrayList<>()).add(itemEvent);
            }
        }
        return histories.values().stream().map(WorkshopPaintable::rehydrate).map(item -> new WorkshopPaintableState(
                item.id(), item.paintableComponentId(), item.paintingProjectId(), item.displayName(), item.workflow(),
                item.currentStage(), item.completed(), item.recipeId(), item.recipeVersion(), item.updatedAt())).toList();
    }
}
