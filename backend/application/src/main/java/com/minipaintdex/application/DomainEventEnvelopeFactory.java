package com.minipaintdex.application;

import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.AggregateRoot;
import com.minipaintdex.domain.event.EventEnvelope;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Creates technical envelopes after an aggregate has completed its business decision. */
public final class DomainEventEnvelopeFactory {
    public List<EventEnvelope> envelop(
            AggregateRoot aggregate,
            Actor actor,
            String correlationId,
            String causationId,
            String idempotencyKey,
            Instant recordedAt) {
        var domainEvents = aggregate.releaseEvents();
        var firstVersion = aggregate.version() - domainEvents.size() + 1;
        var result = new ArrayList<EventEnvelope>(domainEvents.size());
        for (var index = 0; index < domainEvents.size(); index++) {
            var eventKey = idempotencyKey == null || idempotencyKey.isBlank()
                    ? null
                    : index == 0 ? idempotencyKey : idempotencyKey + ":" + (index + 1);
            result.add(new EventEnvelope(
                    Ulid.next(recordedAt),
                    1,
                    firstVersion + index,
                    recordedAt,
                    actor,
                    correlationId,
                    causationId,
                    eventKey,
                    domainEvents.get(index)));
        }
        return List.copyOf(result);
    }
}
