package com.minipaintdex.adapter.file;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventLedger;
import com.minipaintdex.application.port.MarketPaintCatalogWriter;
import com.minipaintdex.application.port.PaintableProductCatalogWriter;
import com.minipaintdex.application.port.PersistenceLifecycle;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.WorkshopPaintInventoryWriter;
import com.minipaintdex.application.validation.DataSnapshotValidator;
import com.minipaintdex.application.port.WorkshopMediaStorage;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.market.product.PaintableProduct;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
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
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

final class FileMiniPaintDexRepository implements SnapshotRepository, EventLedger, MarketPaintCatalogWriter,
        WorkshopPaintInventoryWriter, PaintableProductCatalogWriter, WorkshopMediaStorage, PersistenceLifecycle {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final FileRepositoryLayout layout;
    private final JsonMapper json = JsonMapper.builder().build();
    private final DomainEventCodec eventCodec = new DomainEventCodec();
    private final Object writeMutex = new Object();
    private final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock();
    private final Path writeLockPath;
    private final AtomicVersionedCache<StructuredDocument> siteCache = new AtomicVersionedCache<>("site configuration");
    private final AtomicVersionedCache<List<StructuredDocument>> marketPaintCache = new AtomicVersionedCache<>("market paints");
    private final AtomicVersionedCache<List<StructuredDocument>> paintCatalogEditionCache = new AtomicVersionedCache<>("paint catalog editions");
    private final AtomicVersionedCache<List<StructuredDocument>> workshopPaintCache = new AtomicVersionedCache<>("workshop paints");
    private final AtomicVersionedCache<List<PaintableProduct>> paintableProductCache = new AtomicVersionedCache<>("paintable products");
    private final AtomicVersionedCache<List<StructuredDocument>> paintingGuideCache = new AtomicVersionedCache<>("painting guides");
    private final AtomicVersionedCache<List<StructuredDocument>> shoppingCache = new AtomicVersionedCache<>("shopping list");
    private final AtomicVersionedCache<List<EventEnvelope>> eventCache = new AtomicVersionedCache<>("event ledger");
    private final AtomicReference<PersistenceStatus> persistenceStatus = new AtomicReference<>(new PersistenceStatus(
            "uninitialized", "files", 0, "", null, null, null, "Persistence has not been initialized."));
    private long generation;
    private String persistedMetadataFingerprint = "";

    FileMiniPaintDexRepository(FileRepositoryLayout layout) {
        this.layout = Objects.requireNonNull(layout);
        this.writeLockPath = commonAncestor(
                layout.marketPaintCatalogDirectory(), layout.workshopPaintInventory(), layout.marketPaintableProductsDirectory(),
                layout.paintingGuidesDirectory(), layout.ledgerDirectory()).resolve(".write.lock");
    }

    @Override
    public DataSnapshot load() {
        var read = stateLock.readLock();
        read.lock();
        try {
            return cachedSnapshot();
        } finally {
            read.unlock();
        }
    }

    @Override
    public InitializationReport initialize() {
        try {
            return withExclusiveStorageLock(() -> {
                var now = Instant.now();
                persistenceStatus.set(new PersistenceStatus(
                        "initializing", "files", generation, "", null, now, null, "Loading file repositories."));
                var loaded = loadStableSnapshot();
                validateSnapshot(loaded.snapshot());
                publish(loaded.snapshot(), loaded.metadataFingerprint(), loaded.contentFingerprint(), now);
                return new InitializationReport(
                        persistenceStatus.get(), loaded.snapshot().marketPaints().size(),
                        loaded.snapshot().paintableProducts().size(), loaded.snapshot().events().size());
            });
        } catch (RuntimeException exception) {
            markDegraded("Persistence initialization failed: " + exception.getMessage());
            throw exception;
        }
    }

    @Override
    public RefreshResult refreshIfChanged() {
        try {
            return withExclusiveStorageLock(this::refreshIfChangedLocked);
        } catch (RuntimeException exception) {
            markDegraded("Persistence refresh failed: " + exception.getMessage());
            return new RefreshResult(false, persistenceStatus.get());
        }
    }

    @Override
    public PersistenceStatus status() {
        return persistenceStatus.get();
    }

    private DataSnapshot loadFromDisk() {
        var paintCatalogs = yamlDocuments(layout.marketPaintCatalogDirectory());
        paintCatalogs.forEach(document -> requireSchemaVersion(document, "market paint catalog"));
        var paints = paintCatalogs.stream()
                .flatMap(document -> listOfMaps(document.get("paints")).stream())
                .toList();
        var inventory = yaml(layout.workshopPaintInventory());
        var shopping = yaml(layout.shoppingList());
        requireSchemaVersion(inventory, "workshop paint inventory");
        requireSchemaVersion(shopping, "shopping list");
        var productDocuments = yamlDocuments(layout.marketPaintableProductsDirectory());
        productDocuments.forEach(document -> requireSchemaVersion(document, "paintable product"));
        var products = productDocuments.stream().map(this::product).toList();
        var guideDocuments = yamlDocuments(layout.paintingGuidesDirectory());
        guideDocuments.forEach(document -> requireSchemaVersion(document, "painting guide catalog"));
        var guides = guideDocuments.stream().flatMap(document -> listOfMaps(document.get("painting_guides")).stream()).toList();
        return new DataSnapshot(
                structuredDocument(yaml(layout.siteConfiguration())),
                structuredDocuments(paints),
                structuredDocuments(listOfMaps(inventory.get("paints"))),
                products,
                structuredDocuments(guides),
                structuredDocuments(listOfMaps(shopping.get("items"))),
                readEvents(layout.ledgerDirectory()),
                structuredDocuments(paintCatalogs.stream()
                        .flatMap(document -> listOfMaps(document.get("catalog_editions")).stream()).toList()));
    }

    @Override
    public List<EventEnvelope> appendAll(List<EventEnvelope> events) {
        if (events.isEmpty()) return List.of();
        var month = MONTH.format(events.getFirst().recordedAt());
        if (events.stream().anyMatch(event -> !month.equals(MONTH.format(event.recordedAt())))) {
            throw new FileStorageException("An atomic event batch must target one ledger month.", null);
        }
        return withWriteLock(() -> {
            var currentSnapshot = cachedSnapshot();
            var existing = currentSnapshot.events();
            var existingByKey = existing.stream().filter(event -> event.idempotencyKey() != null)
                    .collect(java.util.stream.Collectors.toMap(EventEnvelope::idempotencyKey, event -> event, (left, right) -> left));
            var incomingKeys = events.stream().map(EventEnvelope::idempotencyKey).filter(java.util.Objects::nonNull).toList();
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
            assertAggregateVersions(existing, events);
            var path = layout.ledgerDirectory().resolve(month + ".jsonl");
            try {
                Files.createDirectories(path.getParent());
                var output = new StringBuilder();
                for (var event : events) output.append(json.writeValueAsString(eventCodec.encode(event))).append(System.lineSeparator());
                var bytes = output.toString().getBytes(StandardCharsets.UTF_8);
                try (var channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                    var buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                var updatedEvents = new ArrayList<>(existing);
                updatedEvents.addAll(events);
                updatedEvents.sort(Comparator.comparing(EventEnvelope::recordedAt).thenComparing(EventEnvelope::eventId));
                publishAfterWrite(new DataSnapshot(
                        currentSnapshot.site(), currentSnapshot.marketPaints(), currentSnapshot.paintInventory(),
                        currentSnapshot.paintableProducts(), currentSnapshot.marketPaintingGuides(),
                        currentSnapshot.shopping(), List.copyOf(updatedEvents), currentSnapshot.paintCatalogEditions()), "Event ledger updated.");
                return List.copyOf(events);
            } catch (IOException exception) {
                throw new FileStorageException("Unable to append event to " + path, exception);
            }
        });
    }

    private static void assertAggregateVersions(
            List<EventEnvelope> existing, List<EventEnvelope> incoming) {
        var versions = new LinkedHashMap<String, Long>();
        existing.forEach(event -> versions.merge(
                aggregateKey(event), event.aggregateVersion(), Math::max));
        for (var event : incoming) {
            var key = aggregateKey(event);
            var expected = versions.getOrDefault(key, 0L) + 1;
            if (event.aggregateVersion() != expected) {
                throw new FileStorageException(
                        "Concurrent aggregate update for " + key + ": expected version "
                                + expected + " but received " + event.aggregateVersion() + ".", null);
            }
            versions.put(key, event.aggregateVersion());
        }
    }

    private static String aggregateKey(EventEnvelope event) {
        return event.aggregateType() + ":" + event.aggregateId();
    }

    @Override
    public void replaceMarketPaints(List<StructuredDocument> paints) {
        replaceMarketPaintCatalog(paints, () -> cachedSnapshot().paintCatalogEditions());
    }

    @Override
    public void replaceMarketPaintCatalog(List<StructuredDocument> paints, List<StructuredDocument> editions) {
        replaceMarketPaintCatalog(paints, () -> editions);
    }

    // Resolve preserved editions under the same write lock as paints, not from an older cache generation.
    private void replaceMarketPaintCatalog(List<StructuredDocument> paints, Supplier<List<StructuredDocument>> editions) {
        withWriteLock(() -> {
            var paintDocuments = paintCatalogDocuments(paints, editions.get());
            replaceYamlBatch(changedPaintCatalogDocuments(paintDocuments));
            removeStalePaintCatalogs(paintDocuments.keySet());
            reloadAfterWrite("Market paint catalogue updated.");
        });
    }

    @Override
    public void replaceWorkshopPaints(List<StructuredDocument> paints) {
        var document = new LinkedHashMap<String, Object>();
        document.put("schema_version", 1);
        document.put("paints", documentMaps(paints));
        withWriteLock(() -> {
            replaceYamlBatch(Map.of(layout.workshopPaintInventory(), document));
            reloadAfterWrite("Workshop paint inventory updated.");
        });
    }

    @Override
    public void replaceMarketPaintsAndWorkshopInventory(
            List<StructuredDocument> paints,
            List<StructuredDocument> inventory,
            WorkshopPaintInventoryWriter ignored) {
        var workshop = new LinkedHashMap<String, Object>();
        workshop.put("schema_version", 1);
        workshop.put("paints", documentMaps(inventory));
        withWriteLock(() -> {
            var documents = new LinkedHashMap<Path, Map<String, Object>>();
            var paintDocuments = paintCatalogDocuments(paints);
            documents.putAll(changedPaintCatalogDocuments(paintDocuments));
            documents.put(layout.workshopPaintInventory(), workshop);
            replaceYamlBatch(documents);
            removeStalePaintCatalogs(paintDocuments.keySet());
            reloadAfterWrite("Market paints and workshop inventory updated.");
        });
    }

    @Override
    public void replaceMarketPaintIdentities(
            List<StructuredDocument> paints,
            List<StructuredDocument> inventory,
            List<StructuredDocument> paintingGuides,
            List<StructuredDocument> shopping) {
        var workshop = new LinkedHashMap<String, Object>();
        workshop.put("schema_version", 1);
        workshop.put("paints", documentMaps(inventory));
        var shoppingDocument = new LinkedHashMap<String, Object>();
        shoppingDocument.put("schema_version", 1);
        shoppingDocument.put("items", documentMaps(shopping));
        withWriteLock(() -> {
            var documents = new LinkedHashMap<Path, Map<String, Object>>();
            var paintDocuments = paintCatalogDocuments(paints);
            documents.putAll(changedPaintCatalogDocuments(paintDocuments));
            documents.put(layout.workshopPaintInventory(), workshop);
            documents.put(layout.shoppingList(), shoppingDocument);
            documents.putAll(paintingGuideDocuments(paintingGuides));
            replaceYamlBatch(documents);
            removeStalePaintCatalogs(paintDocuments.keySet());
            reloadAfterWrite("Market paint identities and references updated.");
        });
    }

    @Override
    public void replaceProduct(
            String productId,
            StructuredDocument product,
            List<StructuredDocument> paintingGuides) {
        var guideDocument = new LinkedHashMap<String, Object>();
        guideDocument.put("schema_version", 1);
        guideDocument.put("product_id", productId);
        guideDocument.put("painting_guides", documentMaps(paintingGuides));
        var documents = new LinkedHashMap<Path, Map<String, Object>>();
        documents.put(layout.marketPaintableProductsDirectory().resolve(productId + ".yaml"), documentMap(product));
        documents.put(layout.paintingGuidesDirectory().resolve(productId + ".yaml"), guideDocument);
        withWriteLock(() -> {
            replaceYamlBatch(documents);
            reloadAfterWrite("Paintable product and painting guides updated.");
        });
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
                number(document.get("schema_version")), productId, text(document.get("name")),
                text(document.get("line")), text(document.get("product_type")), text(document.get("scope")),
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

    private Map<Path, Map<String, Object>> paintCatalogDocuments(List<StructuredDocument> paints) {
        return paintCatalogDocuments(paints, cachedSnapshot().paintCatalogEditions());
    }

    private Map<Path, Map<String, Object>> paintCatalogDocuments(List<StructuredDocument> paints, List<StructuredDocument> editions) {
        var byBrand = new java.util.TreeMap<String, List<Map<String, Object>>>();
        for (var paint : documentMaps(paints)) {
            var brand = text(paint.get("brand"));
            if (brand.isBlank()) throw new FileStorageException("Market paint brand must not be blank.", null);
            byBrand.computeIfAbsent(brand, ignored -> new ArrayList<>()).add(paint);
        }
        var documents = new LinkedHashMap<Path, Map<String, Object>>();
        var editionsByBrand = new java.util.TreeMap<String, List<Map<String, Object>>>();
        for (var edition : documentMaps(editions)) {
            var brand = text(edition.get("brand"));
            byBrand.computeIfAbsent(brand, ignored -> new ArrayList<>());
            editionsByBrand.computeIfAbsent(brand, ignored -> new ArrayList<>()).add(edition);
        }
        byBrand.forEach((brand, records) -> {
            records.sort(Comparator.comparing(record -> text(record.get("id"))));
            var document = new LinkedHashMap<String, Object>();
            document.put("schema_version", 1);
            document.put("brand", brand);
            if (editionsByBrand.containsKey(brand)) document.put("catalog_editions", editionsByBrand.get(brand).stream()
                    .sorted(Comparator.comparing(edition -> text(edition.get("id")))).toList());
            document.put("paints", records);
            documents.put(layout.marketPaintCatalogDirectory().resolve(slug(brand) + ".yaml"), document);
        });
        return documents;
    }

    private Map<Path, Map<String, Object>> paintingGuideDocuments(List<StructuredDocument> guides) {
        var productByCatalogItem = new java.util.HashMap<String, String>();
        paintableProductCache.current().value().forEach(product -> product.catalogItems().forEach(
                item -> productByCatalogItem.put(item.id(), product.id())));
        var byProduct = new java.util.TreeMap<String, List<Map<String, Object>>>();
        for (var guide : documentMaps(guides)) {
            var catalogItemId = text(guide.get("catalog_item_id"));
            var productId = productByCatalogItem.get(catalogItemId);
            if (productId == null) {
                throw new FileStorageException(
                        "Painting guide references an unknown catalog item: " + catalogItemId, null);
            }
            byProduct.computeIfAbsent(productId, ignored -> new ArrayList<>()).add(guide);
        }
        var documents = new LinkedHashMap<Path, Map<String, Object>>();
        byProduct.forEach((productId, records) -> {
            records.sort(Comparator.comparing(record -> text(record.get("id"))));
            var document = new LinkedHashMap<String, Object>();
            document.put("schema_version", 1);
            document.put("product_id", productId);
            document.put("painting_guides", records);
            documents.put(layout.paintingGuidesDirectory().resolve(productId + ".yaml"), document);
        });
        return documents;
    }

    private Map<Path, Map<String, Object>> changedPaintCatalogDocuments(
            Map<Path, Map<String, Object>> desiredDocuments) {
        var currentDocuments = paintCatalogDocuments(marketPaintCache.current().value());
        var changed = new LinkedHashMap<Path, Map<String, Object>>();
        desiredDocuments.forEach((path, document) -> {
            if (!document.equals(currentDocuments.get(path))) changed.put(path, document);
        });
        return changed;
    }

    private void removeStalePaintCatalogs(java.util.Set<Path> catalogPaths) {
        var retained = catalogPaths.stream().map(path -> path.toAbsolutePath().normalize()).collect(java.util.stream.Collectors.toSet());
        for (var existing : files(layout.marketPaintCatalogDirectory(), ".yaml")) {
            if (!retained.contains(existing.toAbsolutePath().normalize())) deleteQuietly(existing);
        }
    }

    private <T> T withExclusiveStorageLock(Supplier<T> operation) {
        synchronized (writeMutex) {
            var write = stateLock.writeLock();
            write.lock();
            try {
                Files.createDirectories(writeLockPath.getParent());
                try (var channel = FileChannel.open(writeLockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     var ignored = channel.lock()) {
                    return operation.get();
                }
            } catch (IOException exception) {
                throw new FileStorageException("Unable to acquire the repository write lock " + writeLockPath, exception);
            } finally {
                write.unlock();
            }
        }
    }

    private <T> T withWriteLock(Supplier<T> operation) {
        return withExclusiveStorageLock(() -> {
            ensureInitialized();
            try {
                refreshIfChangedLocked();
            } catch (RuntimeException exception) {
                markDegraded("Persistence changed before write and could not be refreshed: " + exception.getMessage());
                throw exception;
            }
            return operation.get();
        });
    }

    private void withWriteLock(Runnable operation) {
        withWriteLock(() -> { operation.run(); return null; });
    }

    private RefreshResult refreshIfChangedLocked() {
        ensureInitialized();
        var now = Instant.now();
        var currentMetadata = metadataFingerprint(persistenceFiles());
        if (currentMetadata.equals(persistedMetadataFingerprint)) {
            var previous = persistenceStatus.get();
            var ready = new PersistenceStatus(
                    "ready", "files", previous.generation(), previous.fingerprint(), previous.initializedAt(),
                    now, previous.lastSynchronizedAt(), "Persistence is synchronized with the in-memory caches.");
            persistenceStatus.set(ready);
            return new RefreshResult(false, ready);
        }
        var loaded = loadStableSnapshot();
        validateSnapshot(loaded.snapshot());
        publish(loaded.snapshot(), loaded.metadataFingerprint(), loaded.contentFingerprint(), now);
        return new RefreshResult(true, persistenceStatus.get());
    }

    private void reloadAfterWrite(String detail) {
        var loaded = loadStableSnapshot();
        validateSnapshot(loaded.snapshot());
        publish(loaded.snapshot(), loaded.metadataFingerprint(), loaded.contentFingerprint(), Instant.now(), detail);
    }

    private void publishAfterWrite(DataSnapshot snapshot, String detail) {
        validateSnapshot(snapshot);
        var paths = persistenceFiles();
        publish(snapshot, metadataFingerprint(paths), contentFingerprint(paths), Instant.now(), detail);
    }

    private LoadedSnapshot loadStableSnapshot() {
        var beforePaths = persistenceFiles();
        var before = metadataFingerprint(beforePaths);
        var snapshot = loadFromDisk();
        var afterPaths = persistenceFiles();
        var after = metadataFingerprint(afterPaths);
        if (!before.equals(after)) {
            throw new FileStorageException("Persistence changed while a snapshot was being loaded.", null);
        }
        return new LoadedSnapshot(snapshot, after, contentFingerprint(afterPaths));
    }

    private void publish(DataSnapshot snapshot, String metadataFingerprint, String contentFingerprint, Instant now) {
        publish(snapshot, metadataFingerprint, contentFingerprint, now, "Persistence initialized and synchronized.");
    }

    private void publish(
            DataSnapshot snapshot,
            String metadataFingerprint,
            String contentFingerprint,
            Instant now,
            String detail) {
        generation++;
        siteCache.publish(generation, snapshot.site());
        marketPaintCache.publish(generation, List.copyOf(snapshot.marketPaints()));
        paintCatalogEditionCache.publish(generation, List.copyOf(snapshot.paintCatalogEditions()));
        workshopPaintCache.publish(generation, List.copyOf(snapshot.paintInventory()));
        paintableProductCache.publish(generation, List.copyOf(snapshot.paintableProducts()));
        paintingGuideCache.publish(generation, List.copyOf(snapshot.marketPaintingGuides()));
        shoppingCache.publish(generation, List.copyOf(snapshot.shopping()));
        eventCache.publish(generation, List.copyOf(snapshot.events()));
        persistedMetadataFingerprint = metadataFingerprint;
        var previous = persistenceStatus.get();
        var initializedAt = previous.initializedAt() == null ? now : previous.initializedAt();
        persistenceStatus.set(new PersistenceStatus(
                "ready", "files", generation, contentFingerprint, initializedAt, now, now, detail));
    }

    private DataSnapshot cachedSnapshot() {
        return new DataSnapshot(
                siteCache.current().value(),
                marketPaintCache.current().value(),
                workshopPaintCache.current().value(),
                paintableProductCache.current().value(),
                paintingGuideCache.current().value(),
                shoppingCache.current().value(),
                eventCache.current().value(), paintCatalogEditionCache.current().value());
    }

    private void validateSnapshot(DataSnapshot snapshot) {
        try {
            DataSnapshotValidator.validate(snapshot);
        } catch (com.minipaintdex.domain.shared.DomainException invalidSnapshot) {
            throw new FileStorageException("Invalid data snapshot: " + invalidSnapshot.getMessage(), invalidSnapshot);
        }
    }

    private void ensureInitialized() {
        if (generation == 0) throw new FileStorageException("Persistence has not been initialized.", null);
    }

    private void markDegraded(String detail) {
        var now = Instant.now();
        var previous = persistenceStatus.get();
        persistenceStatus.set(new PersistenceStatus(
                "degraded", "files", previous.generation(), previous.fingerprint(), previous.initializedAt(),
                now, previous.lastSynchronizedAt(), detail));
    }

    private List<Path> persistenceFiles() {
        var required = List.of(
                layout.siteConfiguration(), layout.workshopPaintInventory(), layout.shoppingList());
        for (var path : required) {
            if (!Files.isRegularFile(path)) throw new FileStorageException("Required persistence file is missing: " + path, null);
        }
        var paths = new ArrayList<Path>(required);
        var paintCatalogs = files(layout.marketPaintCatalogDirectory(), ".yaml");
        if (paintCatalogs.isEmpty()) {
            throw new FileStorageException("No market paint brand catalog exists in: "
                    + layout.marketPaintCatalogDirectory(), null);
        }
        paths.addAll(paintCatalogs);
        paths.addAll(files(layout.marketPaintableProductsDirectory(), ".yaml"));
        paths.addAll(files(layout.paintingGuidesDirectory(), ".yaml"));
        paths.addAll(files(layout.ledgerDirectory(), ".jsonl"));
        return paths.stream().map(path -> path.toAbsolutePath().normalize()).distinct()
                .sorted(Comparator.comparing(Path::toString)).toList();
    }

    private String metadataFingerprint(List<Path> paths) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var path : paths) {
                var attributes = Files.readAttributes(path, BasicFileAttributes.class);
                updateDigest(digest, path.toString());
                updateDigest(digest, Long.toString(attributes.size()));
                updateDigest(digest, Long.toString(attributes.lastModifiedTime().toMillis()));
                updateDigest(digest, String.valueOf(attributes.fileKey()));
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new FileStorageException("Unable to fingerprint persistence metadata.", exception);
        }
    }

    private String contentFingerprint(List<Path> paths) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[64 * 1024];
            for (var path : paths) {
                updateDigest(digest, path.toString());
                try (var input = Files.newInputStream(path)) {
                    for (var read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                        if (read > 0) digest.update(buffer, 0, read);
                    }
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new FileStorageException("Unable to fingerprint persistence content.", exception);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private static List<Map<String, Object>> immutableMaps(List<Map<String, Object>> values) {
        return values.stream().map(FileMiniPaintDexRepository::immutableMap).toList();
    }

    private record LoadedSnapshot(
            DataSnapshot snapshot,
            String metadataFingerprint,
            String contentFingerprint) {}

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

    private List<EventEnvelope> readEvents(Path directory) {
        var events = new ArrayList<EventEnvelope>();
        for (var path : files(directory, ".jsonl")) {
            try {
                for (var line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) events.add(eventCodec.decode(json.readValue(line, MAP_TYPE)));
                }
            } catch (IOException exception) {
                throw new FileStorageException("Unable to read ledger " + path, exception);
            }
        }
        events.sort(Comparator.comparing(EventEnvelope::recordedAt).thenComparing(EventEnvelope::eventId));
        return List.copyOf(events);
    }

    private List<Map<String, Object>> yamlDocuments(Path directory) {
        return files(directory, ".yaml").stream().map(this::yaml).toList();
    }

    private Map<String, Object> yaml(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            // SnakeYAML parsers are stateful and not thread-safe. Concurrent REST reads and
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

    private static List<StructuredDocument> structuredDocuments(List<Map<String, Object>> values) {
        return values.stream().map(FileMiniPaintDexRepository::structuredDocument).toList();
    }

    private static StructuredDocument structuredDocument(Map<String, Object> value) {
        return new StructuredDocument(value.entrySet().stream()
                .map(entry -> new StructuredDocument.Field(entry.getKey(), structuredValue(entry.getValue())))
                .toList());
    }

    private static StructuredDocument.Value structuredValue(Object value) {
        if (value == null) return new StructuredDocument.NullValue();
        if (value instanceof Map<?, ?> nested) {
            return new StructuredDocument.ObjectValue(structuredDocument(map(nested)));
        }
        if (value instanceof List<?> list) {
            return new StructuredDocument.ArrayValue(list.stream()
                    .map(FileMiniPaintDexRepository::structuredValue).toList());
        }
        if (value instanceof Number number) return new StructuredDocument.NumberValue(number);
        if (value instanceof Boolean bool) return new StructuredDocument.BooleanValue(bool);
        return new StructuredDocument.Text(String.valueOf(value));
    }

    private static List<Map<String, Object>> documentMaps(List<StructuredDocument> documents) {
        return documents.stream().map(FileMiniPaintDexRepository::documentMap).toList();
    }

    private static Map<String, Object> documentMap(StructuredDocument document) {
        var result = new LinkedHashMap<String, Object>();
        document.fields().forEach(field -> result.put(field.name(), documentValue(field.value())));
        return result;
    }

    private static Object documentValue(StructuredDocument.Value value) {
        return switch (value) {
            case StructuredDocument.Text text -> text.value();
            case StructuredDocument.NumberValue number -> number.value();
            case StructuredDocument.BooleanValue bool -> bool.value();
            case StructuredDocument.NullValue ignored -> null;
            case StructuredDocument.ArrayValue array -> array.values().stream()
                    .map(FileMiniPaintDexRepository::documentValue)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            case StructuredDocument.ObjectValue object -> documentMap(object.value());
        };
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

    private static void requireSchemaVersion(Map<String, Object> document, String owner) {
        if (number(document.get("schema_version")) != 1) {
            throw new FileStorageException(owner + " schema_version must be 1.", null);
        }
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
        var loaderOptions = new LoaderOptions();
        // The paintable-product catalog intentionally shares repeated source lists
        // through YAML anchors, so its complete document needs a higher alias limit.
        loaderOptions.setMaxAliasesForCollections(100_000);
        loaderOptions.setCodePointLimit(64 * 1024 * 1024);
        return new Yaml(loaderOptions, options);
    }

    private static String slug(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(?:^-|-$)", "");
    }
}
