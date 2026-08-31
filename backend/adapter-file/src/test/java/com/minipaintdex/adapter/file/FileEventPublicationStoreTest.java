package com.minipaintdex.adapter.file;

import com.minipaintdex.application.event.EventBatch;
import com.minipaintdex.application.event.EventPublicationStatus;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.workshop.WorkshopCreated;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileEventPublicationStoreTest {
    @TempDir Path directory;

    @Test
    void persistsAndRecoversAnIncompletePublication() {
        var at = Instant.parse("2026-08-30T10:00:00Z");
        var event = new EventEnvelope(
                "event-1", 1, 1, at, new Actor("user", "owner"), "correlation", null, "key",
                new WorkshopCreated("my-workshop", "My workshop", at));
        var batch = new EventBatch("publication-1", "correlation", "key", at, List.of(event));
        var first = new FileEventPublicationStore(directory);
        first.savePending(batch);
        first.markProcessing(batch.batchId(), at.plusSeconds(1));

        var restarted = new FileEventPublicationStore(directory);
        var recovered = restarted.recoverable().getFirst();
        assertEquals(EventPublicationStatus.PROCESSING, recovered.status());
        assertEquals(event, recovered.batch().events().getFirst());

        restarted.markCompleted(batch.batchId(), at.plusSeconds(2));
        assertEquals(List.of(), restarted.recoverable());
    }

    @Test
    void separatesDeadLettersFromRetryablePublications() {
        var at = Instant.parse("2026-08-30T10:00:00Z");
        var event = new EventEnvelope(
                "event-2", 1, 1, at, new Actor("user", "owner"), "correlation", null, "key",
                new WorkshopCreated("my-workshop", "My workshop", at));
        var batch = new EventBatch("publication-2", "correlation", "key", at, List.of(event));
        var store = new FileEventPublicationStore(directory);
        store.savePending(batch);
        store.markProcessing(batch.batchId(), at.plusSeconds(1));
        store.markDeadLetter(batch.batchId(), at.plusSeconds(2), "permanent");

        assertEquals(List.of(), store.recoverable());
        assertEquals(List.of(batch.batchId()), store.deadLetters().stream()
                .map(publication -> publication.publicationId()).toList());
        assertThrows(FileStorageException.class,
                () -> store.markProcessing(batch.batchId(), at.plusSeconds(3)));
    }

    @Test
    void serializesTwoStoreInstancesForTheSameDirectory() throws Exception {
        var at = Instant.parse("2026-08-30T10:00:00Z");
        var event = new EventEnvelope(
                "event-3", 1, 1, at, new Actor("user", "owner"), "correlation", null, "key",
                new WorkshopCreated("my-workshop", "My workshop", at));
        var batch = new EventBatch("publication-3", "correlation", "key", at, List.of(event));
        var first = new FileEventPublicationStore(directory);
        var second = new FileEventPublicationStore(directory);

        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(
                    () -> first.savePending(batch), () -> second.savePending(batch)));
            assertEquals(results.getFirst().get(), results.getLast().get());
        }
        assertEquals(1, first.recoverable().size());
    }
}
