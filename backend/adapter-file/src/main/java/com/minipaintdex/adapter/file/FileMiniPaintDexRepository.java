package com.minipaintdex.adapter.file;

import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventLedger;
import com.minipaintdex.application.port.MarketPaintCatalogWriter;
import com.minipaintdex.application.port.PaintableProductCatalogWriter;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.WorkshopPaintInventoryWriter;
import com.minipaintdex.application.port.WorkshopMediaStorage;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.product.PaintableProduct;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class FileMiniPaintDexRepository implements SnapshotRepository, EventLedger, MarketPaintCatalogWriter, WorkshopPaintInventoryWriter, PaintableProductCatalogWriter, WorkshopMediaStorage {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final FileRepositoryLayout layout;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Object writeMutex = new Object();
    private final Path writeLockPath;

    public FileMiniPaintDexRepository(FileRepositoryLayout layout) {
        this.layout = java.util.Objects.requireNonNull(layout);
        this.writeLockPath = commonAncestor(
                layout.marketPaintCatalog(), layout.workshopPaintInventory(), layout.marketPaintableProductsDirectory(),
                layout.paintingGuidesDirectory(), layout.ledgerDirectory()).resolve(".write.lock");
    }

    @Override
    public DataSnapshot load() {
        var paints = yaml(layout.marketPaintCatalog());
        var inventory = yaml(layout.workshopPaintInventory());
        var shopping = yaml(layout.shoppingList());
        var products = yamlDocuments(layout.marketPaintableProductsDirectory()).stream().map(this::product).toList();
        var guideDocuments = yamlDocuments(layout.paintingGuidesDirectory());
        var guides = guideDocuments.stream().flatMap(document -> listOfMaps(document.get("painting_guides")).stream()).toList();
        return new DataSnapshot(
                yaml(layout.siteConfiguration()),
                listOfMaps(paints.get("paints")),
                listOfMaps(inventory.get("paints")),
                products,
                guides,
                listOfMaps(shopping.get("items")),
                readEvents(layout.ledgerDirectory()));
    }

    @Override
    public List<DomainEvent> appendAll(List<DomainEvent> events) {
        if (events.isEmpty()) return List.of();
        var month = MONTH.format(events.getFirst().recordedAt());
        if (events.stream().anyMatch(event -> !month.equals(MONTH.format(event.recordedAt())))) {
            throw new FileStorageException("An atomic event batch must target one ledger month.", null);
        }
        return withWriteLock(() -> {
            var existing = readEvents(layout.ledgerDirectory());
            var existingByKey = existing.stream().filter(event -> event.idempotencyKey() != null)
                    .collect(java.util.stream.Collectors.toMap(DomainEvent::idempotencyKey, event -> event, (left, right) -> left));
            var incomingKeys = events.stream().map(DomainEvent::idempotencyKey).filter(java.util.Objects::nonNull).toList();
            if (new HashSet<>(incomingKeys).size() != incomingKeys.size()) {
                throw new FileStorageException("An atomic event batch contains duplicate idempotency keys.", null);
            }
            var duplicates = events.stream().filter(event -> event.idempotencyKey() != null)
                    .filter(event -> existingByKey.containsKey(event.idempotencyKey())).toList();
            if (!duplicates.isEmpty()) {
                if (duplicates.size() == events.size()) {
                    return events.stream().map(event -> existingByKey.get(event.idempotencyKey())).toList();
                }
                throw new FileStorageException("A partial idempotency collision would split an atomic event batch.", null);
            }
            var path = layout.ledgerDirectory().resolve(month + ".jsonl");
            try {
                Files.createDirectories(path.getParent());
                var output = new StringBuilder();
                for (var event : events) output.append(json.writeValueAsString(toMap(event))).append(System.lineSeparator());
                var bytes = output.toString().getBytes(StandardCharsets.UTF_8);
                try (var channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                channel.write(ByteBuffer.wrap(bytes));
                channel.force(true);
                }
                return List.copyOf(events);
            } catch (IOException exception) {
                throw new FileStorageException("Unable to append event to " + path, exception);
            }
        });
    }

    @Override
    public void replaceMarketPaints(List<Map<String, Object>> paints) {
        var document = new LinkedHashMap<String, Object>();
        document.put("schema_version", 1);
        document.put("paints", paints);
        withWriteLock(() -> replaceYamlBatch(Map.of(layout.marketPaintCatalog(), document)));
    }

    @Override
    public void replaceWorkshopPaints(List<Map<String, Object>> paints) {
        var document = new LinkedHashMap<String, Object>();
        document.put("schema_version", 1);
        document.put("paints", paints);
        withWriteLock(() -> replaceYamlBatch(Map.of(layout.workshopPaintInventory(), document)));
    }

    @Override
    public void replaceMarketPaintsAndWorkshopInventory(
            List<Map<String, Object>> paints,
            List<Map<String, Object>> inventory,
            WorkshopPaintInventoryWriter ignored) {
        var catalog = new LinkedHashMap<String, Object>();
        catalog.put("schema_version", 1);
        catalog.put("paints", paints);
        var workshop = new LinkedHashMap<String, Object>();
        workshop.put("schema_version", 1);
        workshop.put("paints", inventory);
        var documents = new LinkedHashMap<Path, Map<String, Object>>();
        documents.put(layout.marketPaintCatalog(), catalog);
        documents.put(layout.workshopPaintInventory(), workshop);
        withWriteLock(() -> replaceYamlBatch(documents));
    }

    @Override
    public void replaceProduct(String productId, Map<String, Object> product, List<Map<String, Object>> paintingGuides) {
        var guideDocument = new LinkedHashMap<String, Object>();
        guideDocument.put("schema_version", 1);
        guideDocument.put("product_id", productId);
        guideDocument.put("painting_guides", paintingGuides);
        var documents = new LinkedHashMap<Path, Map<String, Object>>();
        documents.put(layout.marketPaintableProductsDirectory().resolve(productId + ".yaml"), product);
        documents.put(layout.paintingGuidesDirectory().resolve(productId + ".yaml"), guideDocument);
        withWriteLock(() -> replaceYamlBatch(documents));
    }

    @Override
    public StoredMedia store(String itemId, String mediaId, String originalFilename, String contentType, byte[] content) {
        var safeItemId = safeSegment(itemId);
        var extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new FileStorageException("Unsupported workshop media type: " + contentType, null);
        };
        var target = layout.mediaDirectory().resolve("workshop").resolve(safeItemId).resolve(safeSegment(mediaId) + extension).normalize();
        if (!target.startsWith(layout.mediaDirectory())) {
            throw new FileStorageException("Workshop media path escapes the configured media directory.", null);
        }
        withWriteLock(() -> {
            try {
                Files.createDirectories(target.getParent());
                var temporary = Files.createTempFile(target.getParent(), ".minipaintdex-", ".media.tmp");
                try {
                    Files.write(temporary, content, StandardOpenOption.TRUNCATE_EXISTING);
                    atomicReplace(temporary, target);
                } finally {
                    deleteQuietly(temporary);
                }
            } catch (IOException exception) {
                throw new FileStorageException("Unable to store workshop media " + target, exception);
            }
        });
        var relative = layout.mediaDirectory().relativize(target).toString().replace('\\', '/');
        return new StoredMedia(mediaId, "/media/" + relative, relative, originalFilename, contentType, content.length, sha256(content));
    }

    @Override
    public void delete(StoredMedia media) {
        var target = layout.mediaDirectory().resolve(media.storagePath()).normalize();
        if (!target.startsWith(layout.mediaDirectory())) return;
        withWriteLock(() -> deleteQuietly(target));
    }

    private PaintableProduct product(Map<String, Object> document) {
        var edition = map(document.get("edition"));
        var productId = text(document.get("id"));
        var sources = listOfMaps(document.get("sources")).stream().map(this::source).toList();
        var items = listOfMaps(document.get("catalog_items")).stream().map(item -> new PaintableProduct.CatalogItem(
                text(item.get("id")),
                defaultText(text(item.get("product_id")), text(item.get("game_id"))),
                text(item.get("name")), text(item.get("kind")), number(item.get("quantity")),
                text(item.get("description")), Boolean.TRUE.equals(item.get("assembly_required")),
                listOfMaps(item.get("reference_images")).stream().map(image -> new PaintableProduct.ReferenceImage(
                        text(image.get("url")), text(image.get("page_url")), text(image.get("credit")), text(image.get("license")))).toList(),
                listOfMaps(item.get("sources")).stream().map(this::source).toList())).toList();
        return new PaintableProduct(
                number(document.getOrDefault("schema_version", 1)), productId, text(document.get("name")),
                defaultText(text(document.get("line")), text(document.get("game"))),
                defaultText(text(document.get("product_type")), "board_game"), text(document.get("scope")),
                number(document.get("expected_paintable_count")),
                new PaintableProduct.Edition(text(edition.get("note")), text(edition.get("url"))), sources, items);
    }

    private PaintableProduct.Source source(Map<String, Object> source) {
        return new PaintableProduct.Source(text(source.get("kind")), text(source.get("label")), text(source.get("url")));
    }

    private void replaceYamlBatch(Map<Path, Map<String, Object>> documents) {
        var temporaryFiles = new LinkedHashMap<Path, Path>();
        var backups = new LinkedHashMap<Path, Path>();
        var replaced = new ArrayList<Path>();
        try {
            for (var entry : documents.entrySet()) {
                var target = entry.getKey();
                Files.createDirectories(target.getParent());
                var temporary = Files.createTempFile(target.getParent(), ".minipaintdex-", ".yaml.tmp");
                try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    createYaml().dump(entry.getValue(), writer);
                }
                temporaryFiles.put(target, temporary);
                if (Files.exists(target)) {
                    var backup = Files.createTempFile(target.getParent(), ".minipaintdex-", ".yaml.bak");
                    Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
                    backups.put(target, backup);
                }
            }
            for (var entry : temporaryFiles.entrySet()) {
                atomicReplace(entry.getValue(), entry.getKey());
                replaced.add(entry.getKey());
            }
        } catch (IOException exception) {
            for (var target : replaced) {
                var backup = backups.get(target);
                try {
                    if (backup == null) Files.deleteIfExists(target);
                    else atomicReplace(backup, target);
                } catch (IOException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
            }
            throw new FileStorageException("Unable to atomically replace YAML repositories " + documents.keySet(), exception);
        } finally {
            temporaryFiles.values().forEach(FileMiniPaintDexRepository::deleteQuietly);
            backups.values().forEach(FileMiniPaintDexRepository::deleteQuietly);
        }
    }

    private <T> T withWriteLock(Supplier<T> operation) {
        synchronized (writeMutex) {
            try {
                Files.createDirectories(writeLockPath.getParent());
                try (var channel = FileChannel.open(writeLockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     var ignored = channel.lock()) {
                    return operation.get();
                }
            } catch (IOException exception) {
                throw new FileStorageException("Unable to acquire the repository write lock " + writeLockPath, exception);
            }
        }
    }

    private void withWriteLock(Runnable operation) {
        withWriteLock(() -> { operation.run(); return null; });
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    private static String safeSegment(String value) {
        var result = value == null ? "" : value.replaceAll("[^a-zA-Z0-9._-]", "-");
        if (result.isBlank() || ".".equals(result) || "..".equals(result)) {
            throw new FileStorageException("Invalid media path segment.", null);
        }
        return result;
    }

    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Path commonAncestor(Path first, Path... others) {
        var result = first.toAbsolutePath().normalize().getParent();
        for (var other : others) {
            var candidate = other.toAbsolutePath().normalize();
            while (result != null && !candidate.startsWith(result)) result = result.getParent();
        }
        if (result == null) throw new IllegalArgumentException("Repository paths must share a filesystem root.");
        return result;
    }

    private List<DomainEvent> readEvents(Path directory) {
        var events = new ArrayList<DomainEvent>();
        for (var path : files(directory, ".jsonl")) {
            try {
                for (var line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) events.add(event(json.readValue(line, MAP_TYPE)));
                }
            } catch (IOException exception) {
                throw new FileStorageException("Unable to read ledger " + path, exception);
            }
        }
        events.sort(Comparator.comparing(DomainEvent::recordedAt).thenComparing(DomainEvent::eventId));
        return List.copyOf(events);
    }

    private List<Map<String, Object>> yamlDocuments(Path directory) {
        return files(directory, ".yaml").stream().map(this::yaml).toList();
    }

    private Map<String, Object> yaml(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            // SnakeYAML parsers are stateful and not thread-safe. REST bootstrap and
            // facet requests are intentionally concurrent, so each document gets
            // its own parser instance.
            var result = createYaml().<Object>load(reader);
            return map(result);
        } catch (IOException exception) {
            throw new FileStorageException("Unable to read YAML " + path, exception);
        }
    }

    private List<Path> files(Path directory, String suffix) {
        if (!Files.isDirectory(directory)) return List.of();
        try (var paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().endsWith(suffix)).sorted().toList();
        } catch (IOException exception) {
            throw new FileStorageException("Unable to list " + directory, exception);
        }
    }

    private DomainEvent event(Map<String, Object> entry) {
        var actor = map(entry.get("actor"));
        return new DomainEvent(
                text(entry.get("event_id")), number(entry.get("schema_version")), text(entry.get("event_type")),
                Instant.parse(text(entry.get("occurred_at"))), Instant.parse(text(entry.get("recorded_at"))),
                text(entry.get("aggregate_type")), text(entry.get("aggregate_id")), nullable(entry.get("project_id")),
                new Actor(text(actor.get("type")), text(actor.get("id"))), text(entry.get("correlation_id")),
                nullable(entry.get("causation_id")), nullable(entry.get("idempotency_key")), map(entry.get("payload")));
    }

    private Map<String, Object> toMap(DomainEvent event) {
        var result = new LinkedHashMap<String, Object>();
        result.put("event_id", event.eventId());
        result.put("schema_version", event.schemaVersion());
        result.put("event_type", event.eventType());
        result.put("occurred_at", event.occurredAt().toString());
        result.put("recorded_at", event.recordedAt().toString());
        result.put("aggregate_type", event.aggregateType());
        result.put("aggregate_id", event.aggregateId());
        if (event.projectId() != null) result.put("project_id", event.projectId());
        result.put("actor", Map.of("type", event.actor().type(), "id", event.actor().id()));
        result.put("correlation_id", event.correlationId());
        if (event.causationId() != null) result.put("causation_id", event.causationId());
        if (event.idempotencyKey() != null) result.put("idempotency_key", event.idempotencyKey());
        result.put("payload", event.payload());
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(FileMiniPaintDexRepository::map).toList();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullable(Object value) {
        var result = text(value);
        return result.isBlank() ? null : result;
    }

    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value)); } catch (NumberFormatException ignored) { return 0; }
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Yaml createYaml() {
        var options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        return new Yaml(options);
    }
}
