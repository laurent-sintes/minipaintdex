package com.minipaintdex.bootstrap;

import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventPublicationStore;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.domain.event.EventEnvelope;

import java.util.Comparator;
import java.util.LinkedHashMap;

/** Read decorator that includes durably accepted, not-yet-committed events in aggregate decisions. */
final class EffectiveSnapshotRepository implements SnapshotRepository {
    private final SnapshotRepository committed;
    private final EventPublicationStore publications;

    EffectiveSnapshotRepository(SnapshotRepository committed, EventPublicationStore publications) {
        this.committed = committed;
        this.publications = publications;
    }

    @Override
    public DataSnapshot load() {
        var snapshot = committed.load();
        var eventsById = new LinkedHashMap<String, EventEnvelope>();
        snapshot.events().forEach(event -> eventsById.put(event.eventId(), event));
        publications.recoverable().stream()
                .flatMap(publication -> publication.batch().events().stream())
                .forEach(event -> eventsById.putIfAbsent(event.eventId(), event));
        var events = eventsById.values().stream()
                .sorted(Comparator.comparing(EventEnvelope::recordedAt).thenComparing(EventEnvelope::eventId))
                .toList();
        return new DataSnapshot(
                snapshot.site(), snapshot.marketPaints(), snapshot.paintInventory(),
                snapshot.paintableProducts(), snapshot.marketPaintingGuides(), snapshot.shopping(), events, snapshot.paintCatalogEditions());
    }
}
