package com.minipaintdex.application.port;

import com.minipaintdex.application.event.EventBatch;

/** Critical consumer of an atomic event batch. Throwing rejects the acknowledgement and enables retry. */
@FunctionalInterface
public interface EventSubscriber {
    void consume(EventBatch batch);
}
