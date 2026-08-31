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
}
