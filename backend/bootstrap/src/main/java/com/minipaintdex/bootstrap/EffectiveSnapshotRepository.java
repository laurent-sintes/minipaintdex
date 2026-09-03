package com.minipaintdex.bootstrap;

import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventPublicationStore;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.domain.event.EventEnvelope;

import java.util.LinkedHashMap;
import java.util.List;

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
        // Preserve committed sequence and the publication store's ordered batches, including ties in timestamps.
        var events = List.copyOf(eventsById.values());
        return new DataSnapshot(
                snapshot.site(), snapshot.paintProducts(),
                snapshot.paintableProducts(), snapshot.marketPaintingGuides(), snapshot.shopping(), events, snapshot.paintCatalogEditions(), snapshot.paintUsageGuides());
    }
}
