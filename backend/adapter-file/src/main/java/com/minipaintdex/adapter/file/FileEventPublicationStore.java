package com.minipaintdex.adapter.file;

import com.minipaintdex.application.event.EventBatch;
import com.minipaintdex.application.event.EventPublication;
import com.minipaintdex.application.event.EventPublicationStatus;
import com.minipaintdex.application.port.EventPublicationStore;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** File-backed durable publication state using one atomically replaced JSON document per batch. */
public final class FileEventPublicationStore implements EventPublicationStore {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final java.util.concurrent.ConcurrentMap<Path, Object> JVM_LOCKS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final Path directory;
    private final JsonMapper json = JsonMapper.builder().build();
    private final DomainEventCodec eventCodec = new DomainEventCodec();
    private final Object mutex;

    public FileEventPublicationStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        this.mutex = JVM_LOCKS.computeIfAbsent(this.directory, ignored -> new Object());
    }

    @Override
    public EventPublication savePending(EventBatch batch) {
        return withStorageLock(() -> {
            var existing = findUnlocked(batch.batchId());
            if (existing.isPresent()) return existing.get();
            var now = batch.acceptedAt();
            return write(new EventPublication(
                    batch.batchId(), EventPublicationStatus.PENDING, batch, now, now, 0, null));
        });
    }

    @Override
    public EventPublication markProcessing(String publicationId, Instant at) {
        return transition(publicationId, EventPublicationStatus.PROCESSING, at, null, true);
    }

    @Override
    public EventPublication markCompleted(String publicationId, Instant at) {
        return transition(publicationId, EventPublicationStatus.COMPLETED, at, null, false);
    }

    @Override
    public EventPublication markFailed(String publicationId, Instant at, String failure) {
        return transition(publicationId, EventPublicationStatus.FAILED, at, failure, false);
    }

    @Override
    public EventPublication markDeadLetter(String publicationId, Instant at, String failure) {
        return transition(publicationId, EventPublicationStatus.DEAD_LETTER, at, failure, false);
    }

    @Override
    public Optional<EventPublication> find(String publicationId) {
        return withStorageLock(() -> findUnlocked(publicationId));
    }

    @Override
    public List<EventPublication> recoverable() {
        return withStorageLock(() -> {
            if (!Files.isDirectory(directory)) return List.of();
            try (var paths = Files.list(directory)) {
                var result = new ArrayList<EventPublication>();
                for (var path : paths.filter(Files::isRegularFile)
                        .filter(candidate -> candidate.getFileName().toString().endsWith(".json"))
                        .sorted().toList()) {
                    var publication = decode(json.readValue(Files.readString(path, StandardCharsets.UTF_8), MAP_TYPE));
                    if (publication.status() != EventPublicationStatus.COMPLETED
                            && publication.status() != EventPublicationStatus.DEAD_LETTER) {
                        result.add(publication);
                    }
                }
                return List.copyOf(result);
            } catch (IOException | RuntimeException exception) {
                throw new FileStorageException("Unable to list event publications " + directory, exception);
            }
        });
    }

    @Override
    public List<EventPublication> deadLetters() {
        return withStorageLock(() -> {
            if (!Files.isDirectory(directory)) return List.of();
            try (var paths = Files.list(directory)) {
                var result = new ArrayList<EventPublication>();
                for (var path : paths.filter(Files::isRegularFile)
                        .filter(candidate -> candidate.getFileName().toString().endsWith(".json"))
                        .sorted().toList()) {
                    var publication = decode(json.readValue(Files.readString(path, StandardCharsets.UTF_8), MAP_TYPE));
                    if (publication.status() == EventPublicationStatus.DEAD_LETTER) result.add(publication);
                }
                return List.copyOf(result);
            } catch (IOException | RuntimeException exception) {
                throw new FileStorageException("Unable to list dead-letter publications " + directory, exception);
            }
        });
    }

    private EventPublication transition(
            String publicationId, EventPublicationStatus status, Instant at, String failure, boolean incrementAttempt) {
        return withStorageLock(() -> {
            var current = findUnlocked(publicationId).orElseThrow(() ->
                    new FileStorageException("Unknown event publication: " + publicationId, null));
            assertTransition(current.status(), status);
            return write(new EventPublication(
                    current.publicationId(), status, current.batch(), current.createdAt(), at,
                    current.attempts() + (incrementAttempt ? 1 : 0), failure));
        });
    }

    private Optional<EventPublication> findUnlocked(String publicationId) {
        var publicationPath = path(publicationId);
        if (!Files.isRegularFile(publicationPath)) return Optional.empty();
        try {
            return Optional.of(decode(json.readValue(
                    Files.readString(publicationPath, StandardCharsets.UTF_8), MAP_TYPE)));
        } catch (IOException | RuntimeException exception) {
            throw new FileStorageException("Unable to read event publication " + publicationPath, exception);
        }
    }

    private <T> T withStorageLock(Supplier<T> operation) {
        synchronized (mutex) {
            try {
                Files.createDirectories(directory);
                var lockPath = directory.resolve(".publications.lock");
                try (var channel = FileChannel.open(
                        lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     var ignored = channel.lock()) {
                    return operation.get();
                }
            } catch (IOException exception) {
                throw new FileStorageException("Unable to lock event publications " + directory, exception);
            }
        }
    }

    private EventPublication write(EventPublication publication) {
        try {
            Files.createDirectories(directory);
            var target = path(publication.publicationId());
            var temporary = Files.createTempFile(directory, publication.publicationId() + "-", ".tmp");
            try {
                var bytes = json.writeValueAsString(encode(publication)).getBytes(StandardCharsets.UTF_8);
                try (var channel = FileChannel.open(
                        temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    writeFully(channel, ByteBuffer.wrap(bytes));
                    channel.force(true);
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return publication;
        } catch (IOException exception) {
            throw new FileStorageException("Unable to persist event publication " + publication.publicationId(), exception);
        }
    }

    private Map<String, Object> encode(EventPublication publication) {
        var batch = publication.batch();
        var batchMap = new LinkedHashMap<String, Object>();
        batchMap.put("batch_id", batch.batchId());
        batchMap.put("correlation_id", batch.correlationId());
        if (batch.idempotencyKey() != null) batchMap.put("idempotency_key", batch.idempotencyKey());
        batchMap.put("accepted_at", batch.acceptedAt().toString());
        batchMap.put("events", batch.events().stream().map(eventCodec::encode).toList());

        var result = new LinkedHashMap<String, Object>();
        result.put("publication_id", publication.publicationId());
        result.put("status", publication.status().name().toLowerCase());
        result.put("created_at", publication.createdAt().toString());
        result.put("updated_at", publication.updatedAt().toString());
        result.put("attempts", publication.attempts());
        if (publication.failure() != null) result.put("failure", publication.failure());
        result.put("batch", batchMap);
        return result;
    }

    private EventPublication decode(Map<String, Object> value) {
        var batchValue = map(value.get("batch"));
        var batch = new EventBatch(
                text(batchValue.get("batch_id")), text(batchValue.get("correlation_id")),
                nullable(batchValue.get("idempotency_key")), Instant.parse(text(batchValue.get("accepted_at"))),
                listOfMaps(batchValue.get("events")).stream().map(eventCodec::decode).toList());
        return new EventPublication(
                text(value.get("publication_id")),
                EventPublicationStatus.valueOf(text(value.get("status")).toUpperCase()),
                batch,
                Instant.parse(text(value.get("created_at"))),
                Instant.parse(text(value.get("updated_at"))),
                number(value.get("attempts")), nullable(value.get("failure")));
    }

    private Path path(String publicationId) {
        if (publicationId == null || !publicationId.matches("[A-Za-z0-9_-]+")) {
            throw new FileStorageException("Invalid publication id: " + publicationId, null);
        }
        return directory.resolve(publicationId + ".json");
    }

    private static void assertTransition(EventPublicationStatus current, EventPublicationStatus target) {
        var allowed = switch (target) {
            case PROCESSING -> current == EventPublicationStatus.PENDING
                    || current == EventPublicationStatus.PROCESSING
                    || current == EventPublicationStatus.FAILED;
            case COMPLETED, FAILED, DEAD_LETTER -> current == EventPublicationStatus.PROCESSING;
            case PENDING -> false;
        };
        if (!allowed) {
            throw new FileStorageException(
                    "Invalid event publication transition from " + current + " to " + target + ".", null);
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) channel.write(buffer);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(FileEventPublicationStore::map).toList();
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String nullable(Object value) { var result = text(value); return result.isBlank() ? null : result; }
    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException exception) {
            throw new FileStorageException("Expected an integer publication field.", exception);
        }
    }
}
