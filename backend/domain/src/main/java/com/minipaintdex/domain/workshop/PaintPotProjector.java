package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class PaintPotProjector {
    private PaintPotProjector() {}
    public static List<PaintPot> project(List<? extends DomainEvent> events) {
        var histories = new LinkedHashMap<String, List<PaintPotEvent>>();
        for (var candidate : events) {
            var event = candidate instanceof EventEnvelope envelope ? envelope.event() : candidate;
            if (event instanceof PaintPotEvent potEvent) histories.computeIfAbsent(potEvent.paintPotId(), ignored -> new ArrayList<>()).add(potEvent);
        }
        return histories.values().stream().map(PaintPot::rehydrate).toList();
    }
}
