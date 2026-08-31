package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class WorkshopItemProjector {
    private WorkshopItemProjector() {}

    public static List<WorkshopItemState> project(List<? extends DomainEvent> events) {
        var histories = new LinkedHashMap<String, List<WorkshopItemEvent>>();
        for (var candidate : events) {
            var event = candidate instanceof EventEnvelope envelope ? envelope.event() : candidate;
            if (event instanceof WorkshopItemEvent itemEvent) {
                histories.computeIfAbsent(itemEvent.aggregateId(), ignored -> new ArrayList<>()).add(itemEvent);
            }
        }
        return histories.values().stream().map(WorkshopItem::rehydrate).map(item -> new WorkshopItemState(
                item.id(), item.catalogItemId(), item.paintingProjectId(), item.displayName(), item.workflow(),
                item.currentStage(), item.completed(), item.recipeId(), item.recipeVersion(), item.updatedAt())).toList();
    }
}
