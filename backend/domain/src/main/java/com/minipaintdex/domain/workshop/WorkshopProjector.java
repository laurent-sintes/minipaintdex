package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventEnvelope;

import java.util.ArrayList;
import java.util.List;

public final class WorkshopProjector {
    private WorkshopProjector() {}

    public static Workshop project(List<? extends DomainEvent> events) {
        var history = new ArrayList<WorkshopEvent>();
        for (var candidate : events) {
            var event = candidate instanceof EventEnvelope envelope ? envelope.event() : candidate;
            if (event instanceof WorkshopEvent workshopEvent
                    && Workshop.DEFAULT_ID.equals(workshopEvent.aggregateId())) {
                history.add(workshopEvent);
            }
        }
        return Workshop.rehydrate(history);
    }
}
