package com.minipaintdex.application.port;

import com.minipaintdex.domain.event.DomainEvent;

import java.util.List;

public interface EventLedger {
    void append(DomainEvent event);

    default void appendAll(List<DomainEvent> events) {
        events.forEach(this::append);
    }
}
