package com.minipaintdex.adapter.file;

import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventLedger;
import com.minipaintdex.application.port.MarketPaintCatalogWriter;
import com.minipaintdex.application.port.PaintableProductCatalogWriter;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.WorkshopPaintInventoryWriter;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FileMiniPaintDexRepository implements SnapshotRepository, EventLedger, MarketPaintCatalogWriter, WorkshopPaintInventoryWriter, PaintableProductCatalogWriter {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final FileRepositoryLayout layout;
    private final Yaml yaml = createYaml();
    private final JsonMapper json = JsonMapper.builder().build();

    public FileMiniPaintDexRepository(FileRepositoryLayout layout) {
        this.layout = java.util.Objects.requireNonNull(layout);
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
    public void append(DomainEvent event) {
        appendAll(List.of(event));
    }

    @Override
    public void appendAll(List<DomainEvent> events) {
        if (events.isEmpty()) return;
        var month = MONTH.format(events.getFirst().recordedAt());
        if (events.stream().anyMatch(event -> !month.equals(MONTH.format(event.recordedAt())))) {
            throw new FileStorageException("An atomic event batch must target one ledger month.", null);
        }
        var path = layout.ledgerDirectory().resolve(month + ".jsonl");
        try {
            Files.createDirectories(path.getParent());
            var output = new StringBuilder();
            for (var event : events) output.append(json.writeValueAsString(toMap(event))).append(System.lineSeparator());
            var bytes = output.toString().getBytes(StandardCharsets.UTF_8);
            try (var channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
                 var ignored = channel.lock()) {
                channel.write(ByteBuffer.wrap(bytes));
                channel.force(true);
            }
        } catch (IOException exception) {
            throw new FileStorageException("Unable to append event to " + path, exception);
        }
    }

    @Override
    public void replaceMarketPaints(List<Map<String, Object>> paints) {
        var document = new LinkedHashMap<String, Object>();
        document.put("schema_version", 1);
        document.put("paints", paints);
        replaceYaml(layout.marketPaintCatalog(), document);
    }

    @Override
    public void replaceWorkshopPaints(List<Map<String, Object>> paints) {
        var document = new LinkedHashMap<String, Object>();
        document.put("schema_version", 1);
        document.put("paints", paints);
        replaceYaml(layout.workshopPaintInventory(), document);
    }

    @Override
    public void replaceProduct(String productId, Map<String, Object> product, List<Map<String, Object>> paintingGuides) {
        replaceYaml(layout.marketPaintableProductsDirectory().resolve(productId + ".yaml"), product);
        var guideDocument = new LinkedHashMap<String, Object>();
        guideDocument.put("schema_version", 1);
        guideDocument.put("product_id", productId);
        guideDocument.put("painting_guides", paintingGuides);
        replaceYaml(layout.paintingGuidesDirectory().resolve(productId + ".yaml"), guideDocument);
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

    private void replaceYaml(Path target, Map<String, Object> document) {
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "catalog-", ".yaml.tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                yaml.dump(document, writer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new FileStorageException("Unable to replace YAML repository " + target, exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
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
            var result = yaml.<Object>load(reader);
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
