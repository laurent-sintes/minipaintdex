package com.minipaintdex.cli;

import com.minipaintdex.application.usecase.AdministrationUseCases;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.usecase.WorkshopUseCases;
import com.minipaintdex.application.port.EventBus;
import com.minipaintdex.application.port.PersistenceLifecycle;
import com.minipaintdex.application.command.AddWorkshopPaintableCommand;
import com.minipaintdex.application.command.AddWorkshopPaintableCommentCommand;
import com.minipaintdex.application.command.AddWorkshopPaintablePhotoCommand;
import com.minipaintdex.application.command.ApplyPaintProductChangeSetCommand;
import com.minipaintdex.application.command.ApplyMarketPaintableProductChangeSetCommand;
import com.minipaintdex.application.command.AssignWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreateWorkshopRecipeCommand;
import com.minipaintdex.application.command.CreatePaintingProjectCommand;
import com.minipaintdex.application.command.TransitionWorkshopPaintableStageCommand;
import com.minipaintdex.application.command.TransitionWorkshopRecipeCommand;
import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.command.TransitionPaintingProjectCommand;
import com.minipaintdex.application.command.SetShoppingListEntryCheckedCommand;
import com.minipaintdex.application.query.SearchPaintProductsQuery;
import com.minipaintdex.bootstrap.MiniPaintDexSpringConfiguration;
import com.minipaintdex.domain.shared.DomainException;
import com.minipaintdex.domain.workshop.PaintComponent;
import com.minipaintdex.domain.workshop.RecipeSolution;
import com.minipaintdex.domain.workshop.RecipeSolutionType;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

@SpringBootApplication(proxyBeanMethods = false, scanBasePackages = "com.minipaintdex.cli")
@Import(MiniPaintDexSpringConfiguration.class)
@Command(
        name = "minipaintdex",
        mixinStandardHelpOptions = true,
        description = "MiniPaintDex local workshop CLI",
        subcommands = {
                MiniPaintDexCli.Health.class,
                MiniPaintDexCli.Market.class,
                MiniPaintDexCli.Workshop.class,
                MiniPaintDexCli.Datasets.class,
                MiniPaintDexCli.Activity.class,
                MiniPaintDexCli.Projections.class,
                MiniPaintDexCli.Exports.class
        })
public final class MiniPaintDexCli implements Runnable {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    @Option(names = "--root", description = "MiniPaintDex repository root", defaultValue = ".")
    Path root;

    @Option(names = "--format", description = "Output format: human or json", defaultValue = "human")
    String format;

    @Option(names = "--server-url", description = "Running local REST server used as the single writer", defaultValue = "http://127.0.0.1:8080")
    String serverUrl;

    @Option(names = "--wait", description = "Wait until an asynchronous domain-event publication is committed")
    boolean waitForCommit;

    @Option(names = "--wait-timeout", description = "Maximum publication wait as an ISO-8601 duration", defaultValue = "PT30S")
    Duration waitTimeout;

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(350)).build();
    private final MarketCatalogUseCases market;
    private final WorkshopUseCases workshop;
    private final AdministrationUseCases administration;
    private final EventBus eventBus;
    private final PersistenceLifecycle persistence;

    MiniPaintDexCli(
            MarketCatalogUseCases market,
            WorkshopUseCases workshop,
            AdministrationUseCases administration,
            EventBus eventBus,
            PersistenceLifecycle persistence) {
        this.market = Objects.requireNonNull(market);
        this.workshop = Objects.requireNonNull(workshop);
        this.administration = Objects.requireNonNull(administration);
        this.eventBus = Objects.requireNonNull(eventBus);
        this.persistence = Objects.requireNonNull(persistence);
    }

    public static void main(String[] args) {
        var application = new SpringApplication(MiniPaintDexCli.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(defaultProperties(args));
        int exitCode;
        try (var context = application.run(args)) {
            var root = context.getBean(MiniPaintDexCli.class);
            var commandLine = new CommandLine(root);
            commandLine.setExecutionExceptionHandler((exception, command, parseResult) -> {
                if (exception instanceof DomainException domain) {
                    root.error(domain.code(), domain.getMessage());
                    return switch (domain.code()) {
                        case "not_found" -> 4;
                        case "conflict", "invalid_transition" -> 5;
                        default -> 2;
                    };
                }
                exception.printStackTrace(command.getErr());
                return 1;
            });
            exitCode = commandLine.execute(cliArguments(args));
        }
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    MarketCatalogUseCases market() { return market; }
    WorkshopUseCases workshop() { return workshop; }
    AdministrationUseCases administration() { return administration; }

    Map<String, Object> health() {
        var storage = persistence.status();
        var events = eventBus.state();
        return Map.of(
                "status", storage.ready() && events.running() ? "ok" : "degraded",
                "service", "minipaintdex",
                "storage", storage.storage(),
                "persistenceState", storage.state(),
                "eventPipelineRunning", events.running(),
                "recoverablePublications", events.recoverablePublications(),
                "deadLetterPublications", events.deadLetterPublications());
    }

    private static Map<String, Object> defaultProperties(String[] args) {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("spring.main.banner-mode", "off");
        properties.put("logging.level.root", "OFF");
        for (var index = 0; index < args.length; index++) {
            if (args[index].startsWith("--root=")) {
                properties.put("minipaintdex.root", args[index].substring("--root=".length()));
            } else if (args[index].equals("--root") && index + 1 < args.length) {
                properties.put("minipaintdex.root", args[index + 1]);
            }
        }
        return Map.copyOf(properties);
    }

    static String[] cliArguments(String[] args) {
        return Arrays.stream(args)
                .filter(argument -> !argument.startsWith("--minipaintdex."))
                .toArray(String[]::new);
    }

    void output(Object value) {
        System.out.println(json.writeValueAsString(value));
    }

    void error(String code, String message) {
        System.err.println(json.writeValueAsString(Map.of("error", Map.of("code", code, "message", message))));
    }

    Object mutateJson(
            String path,
            Object body,
            String idempotencyKey,
        String correlationId,
        Supplier<Object> offline) throws Exception {
        if (!serverAvailable()) return awaitIfRequested(offline.get(), false);
        var request = HttpRequest.newBuilder(URI.create(serverUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if (idempotencyKey != null && !idempotencyKey.isBlank()) request.header("Idempotency-Key", idempotencyKey);
        if (correlationId != null && !correlationId.isBlank()) request.header("X-Correlation-Id", correlationId);
        var response = http.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new DomainException("remote_error", "Local REST mutation failed with HTTP " + response.statusCode() + ": " + response.body());
        }
        return awaitIfRequested(json.readValue(response.body(), MAP_TYPE), true);
    }

    Object mutatePhoto(
            String photoPath,
            Path file,
            String contentType,
            String stage,
            String caption,
            String actor,
            Instant occurredAt,
            String idempotencyKey,
            String correlationId,
            Boolean removeBackground,
        Supplier<Object> offline) throws Exception {
        if (!serverAvailable()) return awaitIfRequested(offline.get(), false);
        var query = new StringBuilder();
        appendQuery(query, "stage", stage);
        appendQuery(query, "caption", caption);
        appendQuery(query, "actorId", actor);
        appendQuery(query, "occurredAt", occurredAt == null ? null : occurredAt.toString());
        if (removeBackground != null) appendQuery(query, "removeBackground", removeBackground.toString());
        var boundary = "MiniPaintDex-" + java.util.UUID.randomUUID();
        var filename = file.getFileName().toString().replace("\"", "");
        var prefix = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        var suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        var request = HttpRequest.newBuilder(URI.create(
                        serverUrl + photoPath + query))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json");
        if (idempotencyKey != null && !idempotencyKey.isBlank()) request.header("Idempotency-Key", idempotencyKey);
        if (correlationId != null && !correlationId.isBlank()) request.header("X-Correlation-Id", correlationId);
        var response = http.send(request.POST(HttpRequest.BodyPublishers.concat(
                        HttpRequest.BodyPublishers.ofByteArray(prefix),
                        HttpRequest.BodyPublishers.ofFile(file),
                        HttpRequest.BodyPublishers.ofByteArray(suffix))).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new DomainException("remote_error", "Local REST photo mutation failed with HTTP "
                    + response.statusCode() + ": " + response.body());
        }
        return awaitIfRequested(json.readValue(response.body(), MAP_TYPE), true);
    }

    private Object awaitIfRequested(Object outcome, boolean remote) throws Exception {
        if (!waitForCommit) return outcome;
        var publicationId = findText(outcome, "publicationId");
        if (publicationId == null) return outcome;
        var publication = remote ? awaitRemotePublication(publicationId) : eventBus.await(publicationId, waitTimeout);
        return Map.of("outcome", outcome, "committedPublication", publication);
    }

    private Map<String, Object> awaitRemotePublication(String publicationId) throws Exception {
        var deadline = System.nanoTime() + waitTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            var request = HttpRequest.newBuilder(URI.create(serverUrl + "/api/v1/publications/" + publicationId))
                    .timeout(Duration.ofSeconds(5)).header("Accept", "application/json").GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new DomainException("remote_error", "Publication lookup failed with HTTP " + response.statusCode());
            }
            var publication = json.readValue(response.body(), MAP_TYPE);
            var status = findText(publication, "status");
            if ("COMPLETED".equals(status)) return publication;
            if ("FAILED".equals(status)) {
                throw new DomainException("event_publication_failed", "Event publication failed: " + publicationId);
            }
            Thread.sleep(50);
        }
        throw new DomainException("event_publication_timeout", "Timed out waiting for event publication: " + publicationId);
    }

    private static String findText(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            var direct = map.get(key);
            if (direct != null) return String.valueOf(direct);
            for (var nested : map.values()) {
                var found = findText(nested, key);
                if (found != null) return found;
            }
        } else if (value instanceof List<?> list) {
            for (var nested : list) {
                var found = findText(nested, key);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void appendQuery(StringBuilder query, String key, String value) {
        if (value == null || value.isBlank()) return;
        query.append(query.isEmpty() ? '?' : '&').append(key).append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private boolean serverAvailable() {
        try {
            var request = HttpRequest.newBuilder(URI.create(serverUrl + "/actuator/health/liveness"))
                    .timeout(Duration.ofMillis(500)).header("Accept", "application/json").GET().build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception ignored) {
            return false;
        }
    }

    static Map<String, Object> body(Object... keyValues) {
        var result = new LinkedHashMap<String, Object>();
        for (var index = 0; index < keyValues.length; index += 2) {
            if (keyValues[index + 1] != null) result.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return result;
    }

    ApplyPaintProductChangeSetCommand readPaintChangeSet(Path path, boolean dryRun) throws Exception {
        var loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setCodePointLimit(64 * 1024 * 1024);
        var payload = stringMap(new Yaml(new SafeConstructor(loaderOptions)).load(Files.readString(path)));
        var rawOperations = payload.get("operations") instanceof List<?> list ? list : List.of();
        var operations = rawOperations.stream()
                .filter(Map.class::isInstance)
                .map(value -> (Map<?, ?>) value)
                .map(value -> new ApplyPaintProductChangeSetCommand.Operation(
                        String.valueOf(value.get("action")), nullable(value.get("previous_id")),
                        document(stringMap(value.get("record"))),
                        value.get("workshop_quantity_delta") == null ? 0 : number(value.get("workshop_quantity_delta")),
                        Boolean.TRUE.equals(value.get("confirmed_removal"))))
                .toList();
        return new ApplyPaintProductChangeSetCommand(
                number(payload.get("schema_version")), String.valueOf(payload.get("kind")), operations, dryRun,
                (payload.get("catalog_editions") instanceof List<?> editions ? editions : List.of()).stream()
                    .map(value -> document(stringMap(value))).toList(),
                mapList(payload.get("paint_usage_guides")).stream().map(MiniPaintDexCli::document).toList(),
                mapList(payload.get("container_formats")).stream().map(MiniPaintDexCli::document).toList());
    }

    ApplyMarketPaintableProductChangeSetCommand readPaintableProductChangeSet(Path path, boolean dryRun) throws Exception {
        var payload = json.readValue(Files.readString(path), MAP_TYPE);
        return new ApplyMarketPaintableProductChangeSetCommand(
                number(payload.get("schema_version")), String.valueOf(payload.get("kind")),
                document(stringMap(payload.get("product"))),
                mapList(payload.get("painting_guides")).stream().map(MiniPaintDexCli::document).toList(), dryRun,
                nullable(payload.get("actor_id")), nullable(payload.get("correlation_id")));
    }

    CreateWorkshopRecipeCommand readWorkshopRecipe(Path path, String correlationId, String idempotencyKey) throws Exception {
        var payload = json.readValue(Files.readString(path), MAP_TYPE);
        return new CreateWorkshopRecipeCommand(
                nullable(payload.get("recipeId")), String.valueOf(payload.get("paintableComponentId")),
                nullable(payload.get("basedOnGuideId")), nullable(payload.get("supersedesRecipeId")),
                String.valueOf(payload.get("displayName")), number(payload.get("version")),
                mapList(payload.get("solutions")).stream().map(MiniPaintDexCli::recipeSolution).toList(), nullable(payload.get("actorId")),
                payload.get("occurredAt") == null ? null : Instant.parse(String.valueOf(payload.get("occurredAt"))),
                correlationId, idempotencyKey);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stringMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(MiniPaintDexCli::stringMap).toList();
    }

    private static StructuredDocument document(Map<String, Object> values) {
        return new StructuredDocument(values.entrySet().stream()
                .map(entry -> new StructuredDocument.Field(entry.getKey(), documentValue(entry.getValue())))
                .toList());
    }

    private static StructuredDocument.Value documentValue(Object value) {
        if (value == null) return new StructuredDocument.NullValue();
        if (value instanceof Map<?, ?> values) return new StructuredDocument.ObjectValue(document(stringMap(values)));
        if (value instanceof List<?> values) {
            return new StructuredDocument.ArrayValue(values.stream().map(MiniPaintDexCli::documentValue).toList());
        }
        if (value instanceof Number number) return new StructuredDocument.NumberValue(number);
        if (value instanceof Boolean bool) return new StructuredDocument.BooleanValue(bool);
        return new StructuredDocument.Text(String.valueOf(value));
    }

    private static RecipeSolution recipeSolution(Map<String, Object> value) {
        var components = mapList(value.get("components")).stream().map(component -> new PaintComponent(
                String.valueOf(component.get("paintProductId")), decimal(component.getOrDefault("proportion", 1)),
                nullable(component.get("role")))).toList();
        return new RecipeSolution(
                RecipeSolutionType.fromId(String.valueOf(value.get("type"))),
                nullable(value.get("guideSlotId")), nullable(value.get("paintProductId")), components,
                nullable(value.get("instructions")));
    }

    private static String nullable(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    private DatasetDescriptor readDataset(Path input) throws Exception {
        var directory = input.toAbsolutePath().normalize();
        var manifestPath = directory.resolve("dataset.yaml");
        if (!Files.isRegularFile(manifestPath)) {
            throw new DomainException("invalid_input", "dataset.yaml is missing: " + directory);
        }
        Map<String, Object> manifest;
        try (var stream = Files.newInputStream(manifestPath)) {
            manifest = stringMap(new Yaml().load(stream));
        }
        if (number(manifest.get("schema_version")) != 1) {
            throw new DomainException("invalid_input", "Dataset schema_version must be 1.");
        }
        var category = String.valueOf(manifest.get("category"));
        var payload = stringMap(manifest.get("payload"));
        var relativePayload = Path.of(String.valueOf(payload.get("path")));
        var payloadPath = directory.resolve(relativePayload).normalize();
        if (!payloadPath.startsWith(directory) || !Files.isRegularFile(payloadPath)) {
            throw new DomainException("invalid_input", "Dataset payload is missing or escapes its directory.");
        }
        var expectedSha = String.valueOf(payload.get("sha256"));
        var actualSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(payloadPath)));
        if (!actualSha.equals(expectedSha)) {
            throw new DomainException("invalid_input", "Dataset payload checksum does not match.");
        }
        var document = json.readValue(Files.readString(payloadPath), MAP_TYPE);
        if (number(document.get("workshop.paint-pots".equals(String.valueOf(manifest.get("category"))) ? "schemaVersion" : "schema_version")) != 1) {
            throw new DomainException("invalid_input", "Dataset payload schema_version must be 1.");
        }
        return new DatasetDescriptor(category, payloadPath, document);
    }

    private record DatasetDescriptor(String category, Path payloadPath, Map<String, Object> payload) {}

    @Command(name = "health", description = "Check the local application")
    static final class Health implements Callable<Integer> {
        @ParentCommand MiniPaintDexCli root;
        public Integer call() { root.output(root.health()); return 0; }
    }

    @Command(name = "market", mixinStandardHelpOptions = true,
            subcommands = {Market.Paints.class, PaintUsageGuidesCli.class, Market.CatalogEditions.class, Market.PaintableProducts.class, Market.Guides.class, RacksCli.RackProducts.class, RacksCli.ContainerFormats.class})
    static final class Market implements Runnable {
        @ParentCommand MiniPaintDexCli root;
        public void run() { CommandLine.usage(this, System.out); }

        @Command(name = "paint-catalog-editions", mixinStandardHelpOptions = true,
                subcommands = {CatalogEditions.ListEditions.class, CatalogEditions.Show.class})
        static final class CatalogEditions implements Runnable {
            @ParentCommand Market parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "list", mixinStandardHelpOptions = true)
            static final class ListEditions implements Callable<Integer> {
                @ParentCommand CatalogEditions parent;
                @Option(names = "--brand") String brand;
                @Option(names = "--page", defaultValue = "0") int page;
                @Option(names = "--size", defaultValue = "50") int size;
                @Option(names = "--correlation-id") String correlationId;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(root.market().searchPaintCatalogEditions(new com.minipaintdex.application.query.SearchPaintCatalogEditionsQuery(
                            brand, new com.minipaintdex.application.query.PageQuery(page, size, List.of()),
                            correlationId == null ? java.util.UUID.randomUUID().toString() : correlationId)));
                    return 0;
                }
            }
            @Command(name = "show", mixinStandardHelpOptions = true)
            static final class Show implements Callable<Integer> {
                @ParentCommand CatalogEditions parent;
                @Option(names = "--id", required = true) String id;
                @Option(names = "--correlation-id") String correlationId;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(root.market().getPaintCatalogEdition(new com.minipaintdex.application.query.GetPaintCatalogEditionQuery(
                            id, correlationId == null ? java.util.UUID.randomUUID().toString() : correlationId)));
                    return 0;
                }
            }
        }

        @Command(name = "paint-products", mixinStandardHelpOptions = true,
                subcommands = {Paints.Search.class, Paints.Model.class, Paints.Apply.class})
        static final class Paints implements Runnable {
            @ParentCommand Market parent;
            public void run() { CommandLine.usage(this, System.out); }

            @Command(name = "search", mixinStandardHelpOptions = true, description = "Search results, suggestions, or both")
            static final class Search implements Callable<Integer> {
                @ParentCommand Paints parent;
                @picocli.CommandLine.Mixin PaintSearchOptions options;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(root.market().searchPaintProducts(options.query()));
                    return 0;
                }
            }

            @Command(name = "model", mixinStandardHelpOptions = true,
                    description = "Show the canonical market-paint metadata model")
            static final class Model implements Callable<Integer> {
                @ParentCommand Paints parent;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(Map.of("paintModel", root.market().paintProductModel()));
                    return 0;
                }
            }

            @Command(name = "apply", mixinStandardHelpOptions = true,
                    description = "Validate a market-paint change set; mutate only with --apply")
            static final class Apply implements Callable<Integer> {
                @ParentCommand Paints parent;
                @Option(names = "--input", required = true) Path input;
                @Option(names = "--apply", description = "Apply the validated change set") boolean apply;
                public Integer call() throws Exception {
                    var root = parent.parent.root;
                    var dryRun = !apply;
                    var command = root.readPaintChangeSet(input, dryRun);
                    var payload = root.json.readValue(Files.readString(input), MAP_TYPE);
                    root.output(root.mutateJson("/api/v1/market/paint-changesets?dryRun=" + dryRun, payload, null, null,
                            () -> Map.of("result", root.administration().applyPaintProductChangeSet(command))));
                    return 0;
                }
            }
        }

        @Command(name = "paintable-products", mixinStandardHelpOptions = true, subcommands = {
                PaintableProducts.ListProducts.class, PaintableProducts.Show.class,
                PaintableProducts.Apply.class})
        static final class PaintableProducts implements Runnable {
            @ParentCommand Market parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "list")
            static final class ListProducts implements Callable<Integer> {
                @ParentCommand PaintableProducts parent;
                public Integer call() { var root = parent.parent.root; root.output(Map.of("paintableProducts", root.market().listMarketPaintableProducts())); return 0; }
            }
            @Command(name = "show")
            static final class Show implements Callable<Integer> {
                @ParentCommand PaintableProducts parent;
                @Option(names = "--paintable-product-id", required = true) String product;
                public Integer call() { var root = parent.parent.root; root.output(Map.of("paintableProduct", root.market().getMarketPaintableProduct(product))); return 0; }
            }
            @Command(name = "apply", mixinStandardHelpOptions = true,
                    description = "Validate a paintable-product change set; mutate only with --apply")
            static final class Apply implements Callable<Integer> {
                @ParentCommand PaintableProducts parent;
                @Option(names = "--input", required = true) Path input;
                @Option(names = "--apply", description = "Apply the validated change set") boolean apply;
                public Integer call() throws Exception {
                    var root = parent.parent.root;
                    var dryRun = !apply;
                    var command = root.readPaintableProductChangeSet(input, dryRun);
                    var payload = root.json.readValue(Files.readString(input), MAP_TYPE);
                    root.output(root.mutateJson("/api/v1/market/paintable-product-changesets?dryRun=" + dryRun, payload, null, null,
                            () -> Map.of("result", root.administration().applyMarketPaintableProductChangeSet(command))));
                    return 0;
                }
            }
        }

        @Command(name = "guides", subcommands = Guides.ListGuides.class)
        static final class Guides implements Runnable {
            @ParentCommand Market parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "list", description = "List market painting guides")
            static final class ListGuides implements Callable<Integer> {
                @ParentCommand Guides parent;
                @Option(names = "--paintable-component-id") String paintableComponent;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(Map.of("paintingGuides", root.market().listMarketPaintingGuides(paintableComponent)));
                    return 0;
                }
            }
        }
    }

    @Command(name = "workshop", subcommands = {Workshop.Overview.class, Workshop.PaintingProjects.class,
            Workshop.Paintables.class, Workshop.Recipes.class, Workshop.PaintStocks.class, PaintPotsCli.class, ShoppingList.class, RacksCli.WorkshopRacks.class, RacksCli.PaintStorage.class})
    static final class Workshop implements Runnable {
        @ParentCommand MiniPaintDexCli root;
        public void run() { CommandLine.usage(this, System.out); }

        @Command(name = "paint-stocks", mixinStandardHelpOptions = true,
                subcommands = {PaintStocks.Search.class, PaintStocks.Facets.class, PaintStocks.Show.class})
        static final class PaintStocks implements Runnable {
            @ParentCommand Workshop parent;
            public void run() { CommandLine.usage(this, System.out); }

            @Command(name = "show", mixinStandardHelpOptions = true, description = "Read stock and representative photo for one paint product")
            static final class Show implements Callable<Integer> {
                @ParentCommand PaintStocks parent;
                @Option(names = "--paint-product-id", required = true) String paintProductId;
                @Option(names = "--correlation-id") String correlationId;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(root.workshop().getWorkshopPaintStock(new com.minipaintdex.application.query.GetWorkshopPaintStockQuery(
                            paintProductId, correlationId == null ? java.util.UUID.randomUUID().toString() : correlationId)));
                    return 0;
                }
            }


            @Command(name = "search", mixinStandardHelpOptions = true, description = "Search results, suggestions, or both")
            static final class Search implements Callable<Integer> {
                @ParentCommand PaintStocks parent;
                @picocli.CommandLine.Mixin PaintSearchOptions options;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(root.workshop().searchWorkshopPaintStocks(options.query()));
                    return 0;
                }
            }

            @Command(name = "facets", mixinStandardHelpOptions = true)
            static final class Facets implements Callable<Integer> {
                @ParentCommand PaintStocks parent;
                @picocli.CommandLine.Mixin PaintFilterOptions filters;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(root.workshop().workshopPaintStockFacets(filters.query(),
                            filters.manufacturerSheetOnly, filters.realResultOnly));
                    return 0;
                }
            }
        }

        @Command(name = "overview")
        static final class Overview implements Callable<Integer> {
            @ParentCommand Workshop parent;
            public Integer call() { parent.root.output(Map.of("workshop", parent.root.workshop().workshopOverview())); return 0; }
        }

        @Command(name = "painting-projects", subcommands = {
                PaintingProjects.ListProjects.class, PaintingProjects.PreviewImport.class,
                PaintingProjects.Create.class, PaintingProjects.Transition.class})
        static final class PaintingProjects implements Runnable {
            @ParentCommand Workshop parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "list")
            static final class ListProjects implements Callable<Integer> {
                @ParentCommand PaintingProjects parent;
                public Integer call() { var root = parent.parent.root; root.output(Map.of("paintingProjects", root.workshop().listPaintingProjects())); return 0; }
            }
            @Command(name = "preview-import")
            static final class PreviewImport implements Callable<Integer> {
                @ParentCommand PaintingProjects parent;
                @Option(names = "--paintable-product-id", required = true) String product;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(Map.of("preview", root.workshop().previewPaintingProjectImport(product)));
                    return 0;
                }
            }
            @Command(name = "create")
            static final class Create implements Callable<Integer> {
                @ParentCommand PaintingProjects parent;
                @Option(names = "--paintable-product-id", required = true) String product;
                @Option(names = "--painting-project-id") String paintingProjectId;
                @Option(names = "--name") String name;
                @Option(names = "--actor") String actor;
                @Option(names = "--occurred-at") Instant occurredAt;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() throws Exception {
                    var root = parent.parent.root;
                    var command = new CreatePaintingProjectCommand(product, paintingProjectId, name, actor, occurredAt, correlationId, idempotencyKey);
                    root.output(root.mutateJson("/api/v1/workshop/painting-projects",
                            body("paintableProductId", product, "paintingProjectId", paintingProjectId, "name", name,
                                    "actorId", actor, "occurredAt", occurredAt), idempotencyKey, correlationId,
                            () -> Map.of("result", root.workshop().createPaintingProject(command))));
                    return 0;
                }
            }
            @Command(name = "transition")
            static final class Transition implements Callable<Integer> {
                @ParentCommand PaintingProjects parent;
                @Option(names = "--painting-project-id", required = true) String project;
                @Option(names = "--status", required = true) String status;
                @Option(names = "--actor") String actor;
                @Option(names = "--occurred-at") Instant occurredAt;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() throws Exception {
                    var root = parent.parent.root;
                    var command = new TransitionPaintingProjectCommand(
                            project, status, actor, occurredAt, correlationId, idempotencyKey);
                    root.output(root.mutateJson(
                            "/api/v1/workshop/painting-projects/" + project + "/transitions",
                            body("targetStatus", status, "actorId", actor, "occurredAt", occurredAt),
                            idempotencyKey, correlationId,
                            () -> Map.of("publication", root.workshop().transitionPaintingProject(command))));
                    return 0;
                }
            }
        }

        @Command(name = "paintables", subcommands = {Paintables.ListPaintables.class, Paintables.ShowPaintable.class, Paintables.AddPaintable.class, Paintables.Comment.class, Paintables.Photo.class, Stage.class})
        static final class Paintables implements Runnable {
            @ParentCommand Workshop parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "list")
            static final class ListPaintables implements Callable<Integer> {
                @ParentCommand Paintables parent;
                @Option(names = "--painting-project-id") String project;
                public Integer call() { var root = parent.parent.root; root.output(Map.of("paintables", root.workshop().listWorkshopPaintables(project))); return 0; }
            }
            @Command(name = "add")
            static final class AddPaintable implements Callable<Integer> {
                @ParentCommand Paintables parent;
                @Option(names = "--workshop-paintable-id") String workshopPaintableId;
                @Option(names = "--paintable-component-id", required = true) String paintableComponent;
                @Option(names = "--painting-project-id", required = true) String project;
                @Option(names = "--name", required = true) String name;
                @Option(names = "--actor") String actor;
                @Option(names = "--occurred-at") Instant occurredAt;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() throws Exception {
                    var root = parent.parent.root;
                    var command = new AddWorkshopPaintableCommand(workshopPaintableId, paintableComponent, project, name, actor, occurredAt, correlationId, idempotencyKey);
                    root.output(root.mutateJson("/api/v1/workshop/paintables",
                            body("workshopPaintableId", workshopPaintableId, "paintableComponentId", paintableComponent, "paintingProjectId", project,
                                    "displayName", name, "actorId", actor, "occurredAt", occurredAt),
                            idempotencyKey, correlationId, () -> Map.of("publication", root.workshop().addWorkshopPaintable(command))));
                    return 0;
                }
            }
            @Command(name = "show")
            static final class ShowPaintable implements Callable<Integer> {
                @ParentCommand Paintables parent;
                @Option(names = "--workshop-paintable-id", required = true) String item;
                public Integer call() {
                    var root = parent.parent.root;
                    var detail = root.workshop().getWorkshopPaintable(item);
                    var result = root.json.convertValue(detail.paintable(), MAP_TYPE);
                    result.put("activity", detail.activity());
                    root.output(result);
                    return 0;
                }
            }
            @Command(name = "comment")
            static final class Comment implements Callable<Integer> {
                @ParentCommand Paintables parent;
                @Option(names = "--workshop-paintable-id", required = true) String item;
                @Option(names = "--text", required = true) String text;
                @Option(names = "--actor") String actor;
                @Option(names = "--occurred-at") Instant occurredAt;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() throws Exception {
                    var root = parent.parent.root;
                    var command = new AddWorkshopPaintableCommentCommand(item, text, actor, occurredAt, correlationId, idempotencyKey);
                    root.output(root.mutateJson("/api/v1/workshop/paintables/" + item + "/comments",
                            body("comment", text, "actorId", actor, "occurredAt", occurredAt), idempotencyKey, correlationId,
                            () -> Map.of("publication", root.workshop().addWorkshopPaintableComment(command))));
                    return 0;
                }
            }
            @Command(name = "photo")
            static final class Photo implements Callable<Integer> {
                @ParentCommand Paintables parent;
                @Option(names = "--workshop-paintable-id", required = true) String item;
                @Option(names = "--file", required = true) Path file;
                @Option(names = "--stage") String stage;
                @Option(names = "--caption") String caption;
                @Option(names = "--actor") String actor;
                @Option(names = "--occurred-at") Instant occurredAt;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() throws Exception {
                    var root = parent.parent.root;
                    var contentType = Files.probeContentType(file);
                    if (contentType == null) {
                        var name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        contentType = name.endsWith(".png") ? "image/png" : name.endsWith(".webp") ? "image/webp" : "image/jpeg";
                    }
                    var command = new AddWorkshopPaintablePhotoCommand(
                            item, file.getFileName().toString(), contentType, Files.readAllBytes(file), stage, caption,
                            actor, occurredAt, correlationId, idempotencyKey);
                    root.output(root.mutatePhoto("/api/v1/workshop/paintables/" + item + "/photos", file, contentType, stage, caption, actor, occurredAt,
                            idempotencyKey, correlationId, null,
                            () -> Map.of("publication", root.workshop().addWorkshopPaintablePhoto(command))));
                    return 0;
                }
            }
        }

        @Command(name = "stages", subcommands = Stage.Transition.class)
        static final class Stage implements Runnable {
            @ParentCommand Paintables parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "transition")
            static final class Transition implements Callable<Integer> {
                @ParentCommand Stage parent;
                @Option(names = "--workshop-paintable-id", required = true) String item;
                @Option(names = "--stage", required = true) String stage;
                @Option(names = "--action", required = true) String action;
                @Option(names = "--comment") String comment;
                @Option(names = "--reason") String reason;
                @Option(names = "--actor") String actor;
                @Option(names = "--occurred-at") Instant occurredAt;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() throws Exception {
                    var root = parent.parent.parent.root;
                    var command = new TransitionWorkshopPaintableStageCommand(item, stage, action, comment, reason, actor, occurredAt, correlationId, idempotencyKey);
                    root.output(root.mutateJson("/api/v1/workshop/paintables/" + item + "/stage-transitions",
                            body("stage", stage, "action", action, "comment", comment, "reason", reason,
                                    "actorId", actor, "occurredAt", occurredAt), idempotencyKey, correlationId,
                            () -> Map.of("publication", root.workshop().transitionWorkshopPaintableStage(command))));
                    return 0;
                }
            }
        }

        @Command(name = "recipes", subcommands = {
                Recipes.ListRecipes.class, Recipes.ReconcileGuide.class, Recipes.Create.class,
                Recipes.Transition.class, Recipes.Assign.class})
        static final class Recipes implements Runnable {
            @ParentCommand Workshop parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "list")
            static final class ListRecipes implements Callable<Integer> {
                @ParentCommand Recipes parent;
                @Option(names = "--paintable-component-id") String paintableComponent;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(Map.of("recipes", root.workshop().listWorkshopRecipes(paintableComponent)));
                    return 0;
                }
            }
            @Command(name = "reconcile-guide", description = "Rank owned-paint replacements for a market guide")
            static final class ReconcileGuide implements Callable<Integer> {
                @ParentCommand Recipes parent;
                @Option(names = "--guide", required = true) String guide;
                public Integer call() {
                    var root = parent.parent.root;
                    root.output(root.workshop().reconcileMarketPaintingGuide(guide));
                    return 0;
                }
            }
            @Command(name = "create", description = "Create a draft workshop recipe from a JSON command")
            static final class Create implements Callable<Integer> {
                @ParentCommand Recipes parent;
                @Option(names = "--input", required = true) Path input;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() throws Exception {
                    var root = parent.parent.root;
                    var command = root.readWorkshopRecipe(input, correlationId, idempotencyKey);
                    var payload = root.json.readValue(Files.readString(input), MAP_TYPE);
                    root.output(root.mutateJson("/api/v1/workshop/recipes", payload, idempotencyKey, correlationId,
                            () -> Map.of("publication", root.workshop().createWorkshopRecipe(command))));
                    return 0;
                }
            }
            @Command(name = "transition")
            static final class Transition implements Callable<Integer> {
                @ParentCommand Recipes parent;
                @Option(names = "--recipe", required = true) String recipe;
                @Option(names = "--action", required = true) String action;
                @Option(names = "--successor-recipe") String successorRecipe;
                @Option(names = "--reason") String reason;
                @Option(names = "--actor") String actor;
                @Option(names = "--occurred-at") Instant occurredAt;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() throws Exception {
                    var root = parent.parent.root;
                    var command = new TransitionWorkshopRecipeCommand(
                            recipe, action, successorRecipe, reason, actor, occurredAt, correlationId, idempotencyKey);
                    root.output(root.mutateJson("/api/v1/workshop/recipes/" + recipe + "/transitions",
                            body("action", action, "successorRecipeId", successorRecipe, "reason", reason,
                                    "actorId", actor, "occurredAt", occurredAt),
                            idempotencyKey, correlationId,
                            () -> Map.of("publication", root.workshop().transitionWorkshopRecipe(command))));
                    return 0;
                }
            }
            @Command(name = "assign")
            static final class Assign implements Callable<Integer> {
                @ParentCommand Recipes parent;
                @Option(names = "--workshop-paintable-id", required = true) String item;
                @Option(names = "--recipe", required = true) String recipe;
                @Option(names = "--actor") String actor;
                @Option(names = "--occurred-at") Instant occurredAt;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() throws Exception {
                    var root = parent.parent.root;
                    var command = new AssignWorkshopRecipeCommand(item, recipe, actor, occurredAt, correlationId, idempotencyKey);
                    root.output(root.mutateJson("/api/v1/workshop/paintables/" + item + "/recipe-assignments",
                            body("recipeId", recipe, "actorId", actor, "occurredAt", occurredAt),
                            idempotencyKey, correlationId,
                            () -> Map.of("publication", root.workshop().assignWorkshopRecipe(command))));
                    return 0;
                }
            }
        }
    }

    @Command(name = "datasets", subcommands = Datasets.Import.class)
    static final class Datasets implements Runnable {
        @ParentCommand MiniPaintDexCli root;
        public void run() { CommandLine.usage(this, System.out); }

        @Command(name = "import", description = "Validate and import a portable dataset; dry-run unless --apply is present")
        static final class Import implements Callable<Integer> {
            @ParentCommand Datasets parent;
            @Option(names = "--input", required = true) Path input;
            @Option(names = "--apply", description = "Persist the dataset after validation") boolean apply;
            @Option(names = "--actor") String actor;
            @Option(names = "--correlation-id") String correlationId;
            @Option(names = "--idempotency-key") String idempotencyKey;

            public Integer call() throws Exception {
                var root = parent.root;
                var dataset = root.readDataset(input);
                var dryRun = !apply;
                Object result = switch (dataset.category()) {
                    case "market.paint-brand" -> {
                        var command = root.readPaintChangeSet(dataset.payloadPath(), dryRun);
                        yield root.mutateJson(
                                "/api/v1/market/paint-changesets?dryRun=" + dryRun,
                                dataset.payload(), idempotencyKey, correlationId,
                                () -> Map.of("result", root.administration().applyPaintProductChangeSet(command)));
                    }
                    case "market.paintable-product" -> {
                        var command = root.readPaintableProductChangeSet(dataset.payloadPath(), dryRun);
                        yield root.mutateJson(
                                "/api/v1/market/paintable-product-changesets?dryRun=" + dryRun,
                                dataset.payload(), idempotencyKey, correlationId,
                                () -> Map.of("result", root.administration().applyMarketPaintableProductChangeSet(command)));
                    }
                    case "workshop.paint-pots" -> PaintPotsCli.importPayload(root, dataset.payload(), dryRun, actor, correlationId, idempotencyKey);
                    case "workshop.painting-project" -> importPaintingProject(
                            root, dataset.payload(), dryRun, actor, correlationId, idempotencyKey);
                    default -> throw new DomainException(
                            "invalid_input", "Unsupported dataset category: " + dataset.category());
                };
                root.output(Map.of(
                        "dataset", Map.of("category", dataset.category(), "path", input.toString()),
                        "mode", dryRun ? "dry-run" : "apply",
                        "outcome", result));
                return 0;
            }

            private static Object importPaintingProject(
                    MiniPaintDexCli root,
                    Map<String, Object> payload,
                    boolean dryRun,
                    String actor,
                    String correlationId,
                    String idempotencyKey) throws Exception {
                var project = stringMap(payload.get("painting_project"));
                var preview = Map.of(
                        "paintingProjectId", String.valueOf(project.get("id")),
                        "paintableProductId", String.valueOf(project.get("paintable_product_id")),
                        "name", String.valueOf(project.get("name")));
                if (dryRun) {
                    root.workshop().previewPaintingProjectImport(String.valueOf(project.get("paintable_product_id")));
                    return Map.of("result", preview, "applied", false);
                }
                var command = new CreatePaintingProjectCommand(
                        String.valueOf(project.get("paintable_product_id")), String.valueOf(project.get("id")),
                        String.valueOf(project.get("name")), actor, null, correlationId, idempotencyKey);
                return root.mutateJson(
                        "/api/v1/workshop/painting-projects", preview, idempotencyKey, correlationId,
                        () -> Map.of("result", root.workshop().createPaintingProject(command)));
            }
        }
    }

    @Command(name = "shopping-list", mixinStandardHelpOptions = true, subcommands = ShoppingList.Entries.class)
    static final class ShoppingList implements Runnable {
        @ParentCommand Workshop workshopCommand;
        public void run() { CommandLine.usage(this, System.out); }

        @Command(name = "entries", mixinStandardHelpOptions = true, subcommands = {Entries.ListEntries.class, Entries.SetChecked.class})
        static final class Entries implements Runnable {
            @ParentCommand ShoppingList parent;
            public void run() { CommandLine.usage(this, System.out); }
            @Command(name = "list", mixinStandardHelpOptions = true)
            static final class ListEntries implements Callable<Integer> {
                @ParentCommand Entries parent;
                public Integer call() {
                    parent.parent.workshopCommand.root.output(Map.of("entries",
                            parent.parent.workshopCommand.root.workshop().listShoppingListEntries()));
                    return 0;
                }
            }

            @Command(name = "set-checked", mixinStandardHelpOptions = true)
            static final class SetChecked implements Callable<Integer> {
                @ParentCommand Entries parent;
                @Option(names = "--shopping-list-entry-id", required = true) String shoppingListEntryId;
                @Option(names = "--checked", required = true, arity = "1") boolean checked;
                @Option(names = "--actor") String actor;
                @Option(names = "--occurred-at") Instant occurredAt;
                @Option(names = "--correlation-id") String correlationId;
                @Option(names = "--idempotency-key") String idempotencyKey;
                public Integer call() throws Exception {
                    var root = parent.parent.workshopCommand.root;
                    var command = new SetShoppingListEntryCheckedCommand(shoppingListEntryId, checked, actor, occurredAt, correlationId, idempotencyKey);
                    root.output(root.mutateJson("/api/v1/workshop/shopping-list/entries/" + shoppingListEntryId + "/checked",
                            body("checked", checked, "actorId", actor, "occurredAt", occurredAt), idempotencyKey, correlationId,
                            () -> Map.of("publication", root.workshop().setShoppingListEntryChecked(command))));
                    return 0;
                }
            }
        }
    }

    @Command(name = "activity", subcommands = Activity.ListActivity.class)
    static final class Activity implements Runnable {
        @ParentCommand MiniPaintDexCli root;
        public void run() { CommandLine.usage(this, System.out); }
        @Command(name = "list")
        static final class ListActivity implements Callable<Integer> {
            @ParentCommand Activity parent;
            @Option(names = "--painting-project-id") String project;
            public Integer call() { parent.root.output(Map.of("events", parent.root.workshop().listActivity(project))); return 0; }
        }
    }

    @Command(name = "projections", subcommands = Projections.Rebuild.class)
    static final class Projections implements Runnable {
        @ParentCommand MiniPaintDexCli root;
        public void run() { CommandLine.usage(this, System.out); }
        @Command(name = "rebuild")
        static final class Rebuild implements Callable<Integer> {
            @ParentCommand Projections parent;
            public Integer call() { parent.root.output(Map.of("projection", parent.root.administration().rebuildProjections())); return 0; }
        }
    }

    @Command(name = "exports", subcommands = Exports.Paints.class)
    static final class Exports implements Runnable {
        @ParentCommand MiniPaintDexCli root;
        public void run() { CommandLine.usage(this, System.out); }
        @Command(name = "paints")
        static final class Paints implements Callable<Integer> {
            @ParentCommand Exports parent;
            @Option(names = "--type", required = true) String type;
            public Integer call() { System.out.print(parent.root.market().exportPaints(type)); return 0; }
        }
    }
}
