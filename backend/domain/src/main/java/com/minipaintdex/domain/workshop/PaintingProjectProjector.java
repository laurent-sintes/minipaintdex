package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventEnvelope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Projects the current painting-project aggregates from the application ledger. */
public final class PaintingProjectProjector {
    private PaintingProjectProjector() {}

    public static List<PaintingProject> project(List<? extends DomainEvent> events) {
        var histories = new LinkedHashMap<String, List<PaintingProjectEvent>>();
        for (var candidate : events) {
            var event = candidate instanceof EventEnvelope envelope ? envelope.event() : candidate;
            if (event instanceof PaintingProjectEvent projectEvent) {
                histories.computeIfAbsent(projectEvent.aggregateId(), ignored -> new ArrayList<>()).add(projectEvent);
            }
        }
        return histories.values().stream().map(PaintingProject::rehydrate).toList();
    }
}
