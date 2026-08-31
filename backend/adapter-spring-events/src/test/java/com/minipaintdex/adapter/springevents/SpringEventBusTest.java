package com.minipaintdex.adapter.springevents;

import com.minipaintdex.application.event.EventBatch;
import com.minipaintdex.application.event.EventPublication;
import com.minipaintdex.application.event.EventPublicationStatus;
import com.minipaintdex.application.port.EventPublicationStore;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.workshop.WorkshopCreated;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpringEventBusTest {
    @Test
    void acceptsDurablyAndCommitsThroughTheSingleSubscriber() throws Exception {
        var store = new MemoryStore();
        var consumed = new ArrayList<String>();
        var committed = new ArrayList<String>();
        var reference = new AtomicReference<SpringEventBus>();
        var bus = new SpringEventBus(
                event -> reference.get().onApplicationEvent((PublicationAvailable) event),
                store,
                batch -> consumed.add(batch.batchId()),
                batch -> committed.add(batch.batch().batchId()),
                new EventBusSettings(1, 8, 3, Duration.ofMillis(1), Duration.ofSeconds(2)));
        reference.set(bus);
        bus.start();

        var receipt = bus.publish(batch("batch-1"));
        var publication = bus.await(receipt.publicationId(), Duration.ofSeconds(2));
        bus.stop();

        assertEquals(EventPublicationStatus.COMPLETED, publication.status());
        assertEquals(List.of("batch-1"), consumed);
        assertEquals(List.of("batch-1"), committed);
        assertFalse(bus.state().accepting());
    }

    @Test
    void recoversAPendingPublicationOnStartup() throws Exception {
        var store = new MemoryStore();
        store.savePending(batch("recovered"));
        var reference = new AtomicReference<SpringEventBus>();
        var bus = new SpringEventBus(
                event -> reference.get().onApplicationEvent((PublicationAvailable) event),
                store, ignored -> { }, ignored -> { },
                new EventBusSettings(1, 8, 3, Duration.ofMillis(1), Duration.ofSeconds(2)));
        reference.set(bus);

        bus.start();
        var publication = bus.await("recovered", Duration.ofSeconds(2));
        bus.stop();

        assertEquals(EventPublicationStatus.COMPLETED, publication.status());
    }

    @Test
    void awaitDoesNotTreatATransientFailureAsTerminal() throws Exception {
        var store = new MemoryStore();
        var calls = new AtomicInteger();
        var reference = new AtomicReference<SpringEventBus>();
        var bus = new SpringEventBus(
                event -> reference.get().onApplicationEvent((PublicationAvailable) event),
                store,
                ignored -> {
                    if (calls.incrementAndGet() == 1) throw new IllegalStateException("transient");
                },
                ignored -> { },
                new EventBusSettings(1, 8, 3, Duration.ofMillis(1), Duration.ofSeconds(2)));
        reference.set(bus);
        bus.start();

        var receipt = bus.publish(batch("retry"));
        var publication = bus.await(receipt.publicationId(), Duration.ofSeconds(2));
        bus.stop();

        assertEquals(EventPublicationStatus.COMPLETED, publication.status());
        assertEquals(2, calls.get());
    }

    @Test
    void closesIntakeAndDrainsADurablyAcceptedPublicationOnShutdown() {
        var store = new MemoryStore();
        var consumed = new ArrayList<String>();
        var reference = new AtomicReference<SpringEventBus>();
        var bus = new SpringEventBus(
                event -> reference.get().onApplicationEvent((PublicationAvailable) event),
                store, batch -> consumed.add(batch.batchId()), ignored -> { },
                new EventBusSettings(1, 1, 3, Duration.ofSeconds(1), Duration.ofSeconds(2)));
        reference.set(bus);
        bus.start();

        var receipt = bus.publish(batch("shutdown-drain"));
        bus.stop();

        assertEquals(EventPublicationStatus.COMPLETED, store.find(receipt.publicationId()).orElseThrow().status());
        assertEquals(List.of("shutdown-drain"), consumed);
        assertThrows(IllegalStateException.class, () -> bus.publish(batch("rejected-after-stop")));
    }

    @Test
    void keepsPublicationCommittedWhenBestEffortNotificationFails() throws Exception {
        var store = new MemoryStore();
        var reference = new AtomicReference<SpringEventBus>();
        var bus = new SpringEventBus(
                event -> reference.get().onApplicationEvent((PublicationAvailable) event),
                store, ignored -> { }, ignored -> { throw new IllegalStateException("listener failure"); },
                new EventBusSettings(1, 8, 3, Duration.ofMillis(1), Duration.ofSeconds(2)));
        reference.set(bus);
        bus.start();

        var receipt = bus.publish(batch("committed-before-notification"));
        var publication = bus.await(receipt.publicationId(), Duration.ofSeconds(2));
        bus.stop();

        assertEquals(EventPublicationStatus.COMPLETED, publication.status());
        assertEquals(0, store.deadLetters().size());
    }

    @Test
    void movesExhaustedPublicationToDeadLetterAndDegradesState() throws Exception {
        var store = new MemoryStore();
        var reference = new AtomicReference<SpringEventBus>();
        var bus = new SpringEventBus(
                event -> reference.get().onApplicationEvent((PublicationAvailable) event),
                store, ignored -> { throw new IllegalStateException("permanent failure"); }, ignored -> { },
                new EventBusSettings(1, 8, 2, Duration.ofMillis(1), Duration.ofSeconds(2)));
        reference.set(bus);
        bus.start();

        var receipt = bus.publish(batch("dead-letter"));
        var publication = bus.await(receipt.publicationId(), Duration.ofSeconds(2));

        assertEquals(EventPublicationStatus.DEAD_LETTER, publication.status());
        assertEquals(0, bus.state().recoverablePublications());
        assertEquals(1, bus.state().deadLetterPublications());
        bus.stop();
    }

    private static EventBatch batch(String id) {
        var at = Instant.parse("2026-08-30T10:00:00Z");
        var event = new EventEnvelope(
                id + "-event", 1, 1, at, new Actor("user", "owner"), id, null, id,
                new WorkshopCreated("my-workshop", "My workshop", at));
        return new EventBatch(id, id, id, at, List.of(event));
    }

    private static final class MemoryStore implements EventPublicationStore {
        private final Map<String, EventPublication> publications = new LinkedHashMap<>();

        @Override public synchronized EventPublication savePending(EventBatch batch) {
            return publications.computeIfAbsent(batch.batchId(), ignored -> new EventPublication(
                    batch.batchId(), EventPublicationStatus.PENDING, batch,
                    batch.acceptedAt(), batch.acceptedAt(), 0, null));
        }
        @Override public synchronized EventPublication markProcessing(String id, Instant at) {
            return transition(id, EventPublicationStatus.PROCESSING, at, null, true);
        }
        @Override public synchronized EventPublication markCompleted(String id, Instant at) {
            return transition(id, EventPublicationStatus.COMPLETED, at, null, false);
        }
        @Override public synchronized EventPublication markFailed(String id, Instant at, String failure) {
            return transition(id, EventPublicationStatus.FAILED, at, failure, false);
        }
        @Override public synchronized EventPublication markDeadLetter(String id, Instant at, String failure) {
            return transition(id, EventPublicationStatus.DEAD_LETTER, at, failure, false);
        }
        @Override public synchronized Optional<EventPublication> find(String id) {
            return Optional.ofNullable(publications.get(id));
        }
        @Override public synchronized List<EventPublication> recoverable() {
            return publications.values().stream()
                    .filter(value -> value.status() != EventPublicationStatus.COMPLETED)
                    .filter(value -> value.status() != EventPublicationStatus.DEAD_LETTER).toList();
        }
        @Override public synchronized List<EventPublication> deadLetters() {
            return publications.values().stream()
                    .filter(value -> value.status() == EventPublicationStatus.DEAD_LETTER).toList();
        }
        private EventPublication transition(String id, EventPublicationStatus status, Instant at, String failure, boolean attempt) {
            var current = publications.get(id);
            var updated = new EventPublication(id, status, current.batch(), current.createdAt(), at,
                    current.attempts() + (attempt ? 1 : 0), failure);
            publications.put(id, updated);
            return updated;
        }
    }
}
