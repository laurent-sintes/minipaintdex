package com.minipaintdex.application.port;

import com.minipaintdex.domain.event.DomainEvent;

public interface EventLedger {
    void append(DomainEvent event);
}
