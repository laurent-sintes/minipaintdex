package com.minipaintdex.application.port;

import com.minipaintdex.application.event.CommittedEventBatch;

/** Publishes non-critical notifications after the ledger has committed a batch. */
@FunctionalInterface
public interface CommittedEventPublisher {
    /** Notifies best-effort consumers only after the critical ledger acknowledgement. */
    void publish(CommittedEventBatch batch);
}
