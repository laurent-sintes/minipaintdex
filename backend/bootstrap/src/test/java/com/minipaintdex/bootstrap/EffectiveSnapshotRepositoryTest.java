package com.minipaintdex.bootstrap;

import com.minipaintdex.adapter.file.FileEventPublicationStore;
import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.event.EventBatch;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.workshop.PaintingProjectCreated;
import com.minipaintdex.domain.workshop.PaintingProjectProjector;
import com.minipaintdex.domain.workshop.PaintingProjectStatus;
import com.minipaintdex.domain.workshop.PaintingProjectStatusChanged;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EffectiveSnapshotRepositoryTest {
    @TempDir
    Path root;

    @Test
    void preservesPendingBatchOrderAndDeduplicatesAlreadyCommittedEvents() {
        var at = Instant.parse("2026-08-30T10:00:00Z");
        var created = new EventEnvelope("zz-created", 1, 1, at, new Actor("user", "owner"),
                "create-project", null, "create-project", new PaintingProjectCreated(
                        "paint-game", "my-workshop", "game", "Paint Game", 1, at));
        var activated = new EventEnvelope("aa-activated", 1, 2, at, new Actor("user", "owner"),
                "create-project", null, "activate-project", new PaintingProjectStatusChanged(
                        "paint-game", PaintingProjectStatus.ACTIVE, at));
        var events = List.of(created, activated);
        var publications = new FileEventPublicationStore(root.resolve("publications"));
        publications.savePending(new EventBatch("create-project", "create-project", "create-project", at, events));

        for (var committedEvents : List.of(List.<EventEnvelope>of(), events)) {
            var committed = snapshot(new StructuredDocument(List.of()), List.of(), List.of(),
                    List.of(), List.of(), List.of(), committedEvents, List.of());
            var effective = new EffectiveSnapshotRepository(() -> committed, publications).load();

            assertEquals(events, effective.events());
            assertEquals(PaintingProjectStatus.ACTIVE,
                    PaintingProjectProjector.project(effective.events()).getFirst().status());
        }
    }

    private static com.minipaintdex.application.port.DataSnapshot snapshot(
            com.minipaintdex.application.document.StructuredDocument site,
            java.util.List<com.minipaintdex.application.document.StructuredDocument> paints,
            java.util.List<com.minipaintdex.application.document.StructuredDocument> stocks,
            java.util.List<com.minipaintdex.domain.market.product.PaintableProduct> products,
            java.util.List<com.minipaintdex.application.document.StructuredDocument> guides,
            java.util.List<com.minipaintdex.application.document.StructuredDocument> shopping,
            java.util.List<com.minipaintdex.domain.event.EventEnvelope> history,
            java.util.List<com.minipaintdex.application.document.StructuredDocument> editions) {
        var events = new java.util.ArrayList<com.minipaintdex.domain.event.EventEnvelope>();
        for (var stock : com.minipaintdex.application.validation.StructuredDocuments.toMaps(stocks)) {
            var id = String.valueOf(stock.get("paint_id"));
            var quantity = ((Number) stock.get("quantity")).intValue();
            for (var ordinal = 1; ordinal <= quantity; ordinal++) {
                var potId = "pot-test-" + id + "-" + ordinal;
                if (history.stream().anyMatch(event -> potId.equals(event.aggregateId()))) continue;
                var pot = com.minipaintdex.domain.workshop.PaintPot.register(potId, id, null, java.time.Instant.EPOCH);
                events.add(new com.minipaintdex.domain.event.EventEnvelope(potId, 1, 1, java.time.Instant.EPOCH,
                        new com.minipaintdex.domain.event.Actor("user", "owner"), "fixture", null, potId, pot.releaseEvents().getFirst()));
            }
        }
        events.addAll(history);
        return new DataSnapshot(site, paints, products, guides, shopping, events, editions, java.util.List.of());
    }

}
