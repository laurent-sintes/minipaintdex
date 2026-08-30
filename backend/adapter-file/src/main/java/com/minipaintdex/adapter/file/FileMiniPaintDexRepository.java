package com.minipaintdex.adapter.file;

import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventLedger;
import com.minipaintdex.application.port.MarketPaintCatalogWriter;
import com.minipaintdex.application.port.ProjectCatalogWriter;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.WorkshopPaintInventoryWriter;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
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

public final class FileMiniPaintDexRepository implements SnapshotRepository, EventLedger, MarketPaintCatalogWriter, WorkshopPaintInventoryWriter, ProjectCatalogWriter {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final Path root;
    private final Yaml yaml = createYaml();
    private final JsonMapper json = JsonMapper.builder().build();

    public FileMiniPaintDexRepository(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    @Override
    public DataSnapshot load() {
        var data = root.resolve("data");
        var paints = yaml(data.resolve("market/paints/catalog.yaml"));
        var inventory = yaml(data.resolve("workshop/paints.yaml"));
        var shopping = yaml(data.resolve("workshop/shopping.yaml"));
        var games = yamlDocuments(data.resolve("market/games"));
        var guideDocuments = yamlDocuments(data.resolve("market/painting-guides"));
        var guides = guideDocuments.stream().flatMap(document -> listOfMaps(document.get("painting_guides")).stream()).toList();
        return new DataSnapshot(
                yaml(data.resolve("site/fr.yaml")),
                listOfMaps(paints.get("paints")),
                listOfMaps(inventory.get("paints")),
                games,
                guides,
                listOfMaps(shopping.get("items")),
                readEvents(data.resolve("ledger/events")));
    }

    @Override
    public void append(DomainEvent event) {
        var path = root.resolve("data/ledger/events").resolve(MONTH.format(event.recordedAt()) + ".jsonl");
        try {
            Files.createDirectories(path.getParent());
            var bytes = (json.writeValueAsString(toMap(event)) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
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
        var target = root.resolve("data/market/paints/catalog.yaml");
        var document = new LinkedHashMap<String, Object>();
        document.put("schema_version", 1);
        document.put("paints", paints);
        replaceYaml(target, document);
    }

    @Override
    public void replaceWorkshopPaints(List<Map<String, Object>> paints) {
        var target = root.resolve("data/workshop/paints.yaml");
        var document = new LinkedHashMap<String, Object>();
        document.put("schema_version", 1);
        document.put("paints", paints);
        replaceYaml(target, document);
    }

    @Override
    public void replaceProject(String projectId, Map<String, Object> project, List<Map<String, Object>> paintingGuides) {
        replaceYaml(root.resolve("data/market/games").resolve(projectId + ".yaml"), project);
        var guideDocument = new LinkedHashMap<String, Object>();
        guideDocument.put("schema_version", 1);
        guideDocument.put("project_id", projectId);
        guideDocument.put("painting_guides", paintingGuides);
        replaceYaml(root.resolve("data/market/painting-guides").resolve(projectId + ".yaml"), guideDocument);
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
        return value instanceof Number number ? number.intValue() : Integer.parseInt(text(value));
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
