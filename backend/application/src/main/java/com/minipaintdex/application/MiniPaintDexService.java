package com.minipaintdex.application;

import com.minipaintdex.application.command.AddWorkshopItemCommand;
import com.minipaintdex.application.command.TransitionStageCommand;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.EventLedger;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.domain.event.Actor;
import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.workflow.DomainException;
import com.minipaintdex.domain.workflow.StageAction;
import com.minipaintdex.domain.workflow.WorkflowStage;
import com.minipaintdex.domain.workflow.WorkflowStageStatus;
import com.minipaintdex.domain.workshop.WorkshopItemProjector;
import com.minipaintdex.domain.workshop.WorkshopItemState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MiniPaintDexService {
    private final SnapshotRepository snapshots;
    private final EventLedger ledger;

    public MiniPaintDexService(SnapshotRepository snapshots, EventLedger ledger) {
        this.snapshots = Objects.requireNonNull(snapshots);
        this.ledger = Objects.requireNonNull(ledger);
    }

    public Map<String, Object> bootstrap() {
        return project(snapshots.load());
    }

    public List<Map<String, Object>> searchMarketPaints(Map<String, String> filters) {
        @SuppressWarnings("unchecked")
        var paints = (List<Map<String, Object>>) bootstrap().get("paints");
        var query = text(filters.get("query")).toLowerCase(Locale.FRENCH);
        return paints.stream().filter(paint -> {
            if (present(filters.get("brand")) && !Objects.equals(filters.get("brand"), paint.get("brand"))) return false;
            if (present(filters.get("range")) && !Objects.equals(filters.get("range"), paint.get("range"))) return false;
            if (present(filters.get("type")) && !Objects.equals(filters.get("type"), paint.get("paintType"))) return false;
            if (present(filters.get("color")) && !Objects.equals(filters.get("color"), paint.get("colorFamily"))) return false;
            if (!query.isBlank()) {
                var haystack = String.join(" ", text(paint.get("name")), text(paint.get("brand")), text(paint.get("range")), text(paint.get("reference")), text(paint.get("tags"))).toLowerCase(Locale.FRENCH);
                if (!haystack.contains(query)) return false;
            }
            return true;
        }).toList();
    }

    public Map<String, Object> getMarketPaint(String id) {
        return searchMarketPaints(Map.of()).stream().filter(paint -> id.equals(paint.get("id"))).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Paint not found: " + id));
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listProjects() {
        return (List<Map<String, Object>>) bootstrap().get("projects");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listWorkshopItems(String projectId) {
        var items = (List<Map<String, Object>>) bootstrap().get("workshopItems");
        if (!present(projectId)) return items;
        return items.stream().filter(item -> projectId.equals(item.get("projectId"))).toList();
    }

    public List<DomainEvent> listActivity(String projectId) {
        return snapshots.load().events().stream()
                .filter(event -> !present(projectId) || projectId.equals(event.projectId()))
                .sorted(Comparator.comparing(DomainEvent::recordedAt).reversed())
                .toList();
    }

    public DomainEvent addWorkshopItem(AddWorkshopItemCommand command) {
        require(command.catalogItemId(), "catalogItemId");
        require(command.projectId(), "projectId");
        require(command.displayName(), "displayName");
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return duplicate;
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var itemId = present(command.itemId()) ? command.itemId() : "ws-" + command.catalogItemId() + "-" + Ulid.next(occurredAt).toLowerCase(Locale.ROOT);
        if (snapshot.events().stream().anyMatch(event -> itemId.equals(event.aggregateId()) && "workshop_item.added".equals(event.eventType()))) {
            throw new DomainException("conflict", "Workshop item already exists: " + itemId);
        }
        var event = new DomainEvent(Ulid.next(occurredAt), 1, "workshop_item.added", occurredAt, Instant.now(),
                "workshop_item", itemId, command.projectId(), new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(),
                Map.of("catalog_item_id", command.catalogItemId(), "display_name", command.displayName()));
        ledger.append(event);
        return event;
    }

    public DomainEvent transitionStage(TransitionStageCommand command) {
        require(command.itemId(), "itemId");
        var stage = WorkflowStage.fromId(command.stage());
        var action = StageAction.fromId(command.action());
        var snapshot = snapshots.load();
        var duplicate = idempotent(snapshot, command.idempotencyKey());
        if (duplicate != null) return duplicate;
        var item = WorkshopItemProjector.project(snapshot.events()).stream().filter(candidate -> command.itemId().equals(candidate.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Workshop item not found: " + command.itemId()));
        WorkshopItemProjector.assertTransition(item.workflow().get(stage), action);
        var occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("stage", stage.id());
        if (present(command.comment())) payload.put("comment", command.comment());
        if (present(command.reason())) payload.put("reason", command.reason());
        var event = new DomainEvent(Ulid.next(occurredAt), 1, action.eventType(), occurredAt, Instant.now(),
                "workshop_item", item.id(), item.projectId(), new Actor("user", defaultText(command.actorId(), "owner")),
                defaultText(command.correlationId(), Ulid.next(Instant.now())), null, command.idempotencyKey(), payload);
        ledger.append(event);
        return event;
    }

    public Map<String, Object> rebuildProjections() {
        var view = bootstrap();
        return Map.of(
                "paints", size(view.get("paints")),
                "projects", size(view.get("projects")),
                "workshopItems", size(view.get("workshopItems")));
    }

    public String exportPaints(String format) {
        var paints = searchMarketPaints(Map.of());
        if ("csv".equals(format)) {
            var rows = new ArrayList<String>();
            rows.add("id,brand,range,reference,name,color_hex,color_family,finish,medium,volume_ml,quantity");
            for (var paint : paints) rows.add(List.of("id", "brand", "range", "reference", "name", "colorHex", "colorFamily", "finish", "medium", "volumeMl", "quantity").stream().map(key -> csv(paint.get(key))).collect(Collectors.joining(",")));
            return String.join("\n", rows) + "\n";
        }
        if ("yaml".equals(format)) {
            var output = new StringBuilder("paints:\n");
            for (var paint : paints) {
                output.append("  - id: ").append(quoted(paint.get("id"))).append('\n');
                output.append("    brand: ").append(quoted(paint.get("brand"))).append('\n');
                output.append("    range: ").append(quoted(paint.get("range"))).append('\n');
                output.append("    reference: ").append(quoted(paint.get("reference"))).append('\n');
                output.append("    name: ").append(quoted(paint.get("name"))).append('\n');
                output.append("    quantity: ").append(number(paint.get("quantity"))).append('\n');
            }
            return output.toString();
        }
        throw new DomainException("not_found", "Unknown export format: " + format);
    }

    private Map<String, Object> project(DataSnapshot snapshot) {
        var items = WorkshopItemProjector.project(snapshot.events());
        var paints = paintViews(snapshot);
        var result = new LinkedHashMap<String, Object>();
        result.put("paints", paints);
        result.put("projects", projectViews(snapshot, paints, items));
        result.put("workshopItems", items.stream().map(this::workshopItemView).toList());
        result.put("shoppingSeed", shoppingViews(snapshot));
        result.put("config", camelize(snapshot.site()));
        return result;
    }

    private List<Map<String, Object>> paintViews(DataSnapshot snapshot) {
        var quantities = snapshot.paintInventory().stream().collect(Collectors.toMap(entry -> text(entry.get("paint_id")), entry -> number(entry.get("quantity")), Integer::sum));
        return snapshot.marketPaints().stream().map(entry -> {
            var color = map(entry.get("color"));
            var manufacturerImage = map(entry.get("manufacturer_image"));
            var resultImage = map(entry.get("result_image"));
            var verifiedAt = text(entry.get("verified_at"));
            Map<String, Object> paint = new LinkedHashMap<>();
            paint.put("id", text(entry.get("id")));
            paint.put("brand", text(entry.get("brand")));
            paint.put("manufacturer", text(entry.get("manufacturer")));
            paint.put("brandAliases", strings(entry.get("brand_aliases")));
            paint.put("range", text(entry.get("range")));
            paint.put("paintType", text(entry.get("functional_type")));
            paint.put("reference", text(entry.get("reference")));
            paint.put("name", text(entry.get("name")));
            paint.put("colorHex", defaultText(text(color.get("hex")), "#777777"));
            paint.put("finish", text(entry.get("finish")));
            paint.put("medium", text(entry.get("medium")));
            paint.put("quantity", quantities.getOrDefault(text(entry.get("id")), 0));
            paint.put("status", text(entry.get("data_status")));
            paint.put("warnings", String.join(" · ", strings(entry.get("warnings"))));
            paint.put("tags", strings(entry.get("tags")));
            paint.put("notes", text(entry.get("notes")));
            paint.put("createdAt", verifiedAt.isBlank() ? "" : verifiedAt + "T00:00:00.000Z");
            paint.put("updatedAt", verifiedAt.isBlank() ? "" : verifiedAt + "T00:00:00.000Z");
            paint.put("manufacturerUrl", text(entry.get("manufacturer_page")));
            paint.put("manufacturerImage", text(manufacturerImage.get("path")));
            paint.put("manufacturerImageCredit", text(manufacturerImage.get("credit")));
            paint.put("volumeMl", number(entry.get("volume_ml")));
            paint.put("colorFamily", text(color.get("family")));
            paint.put("manufacturerDescription", text(entry.get("notes")));
            paint.put("recommendedUses", strings(entry.get("recommended_uses")));
            paint.put("manufacturerVerifiedAt", verifiedAt);
            paint.put("resultImage", text(resultImage.get("path")));
            paint.put("resultImageCredit", text(resultImage.get("credit")));
            paint.put("resultImageSource", text(resultImage.get("source_url")));
            paint.put("resultImageLicense", text(resultImage.get("license")));
            paint.put("resultReferenceUrl", text(resultImage.get("reference_url")));
            return paint;
        }).sorted(Comparator.comparing(entry -> text(entry.get("name")), String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private List<Map<String, Object>> projectViews(DataSnapshot snapshot, List<Map<String, Object>> paints, List<WorkshopItemState> workshopItems) {
        var recipes = snapshot.recipes().stream().collect(Collectors.toMap(entry -> text(entry.get("catalog_item_id")), Function.identity()));
        var paintsById = paints.stream().collect(Collectors.toMap(entry -> text(entry.get("id")), Function.identity()));
        return snapshot.games().stream().map(game -> {
            var gameId = text(game.get("id"));
            var projectItems = listOfMaps(game.get("catalog_items")).stream().map(item -> {
                var catalogItemId = text(item.get("id"));
                var recipe = recipes.getOrDefault(catalogItemId, Map.of());
                var physicalItems = workshopItems.stream().filter(candidate -> catalogItemId.equals(candidate.catalogItemId())).toList();
                var allCompleted = !physicalItems.isEmpty() && physicalItems.stream().allMatch(WorkshopItemState::completed);
                var anyStarted = physicalItems.stream().anyMatch(candidate -> candidate.workflow().values().stream().anyMatch(status -> status != WorkflowStageStatus.PENDING));
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("id", catalogItemId.startsWith(gameId + "-") ? catalogItemId.substring(gameId.length() + 1) : catalogItemId);
                view.put("name", text(item.get("name")));
                view.put("kind", text(item.get("kind")));
                view.put("quantity", physicalItems.size());
                view.put("status", allCompleted ? "terminé" : anyStarted ? "en cours" : "à préparer");
                view.put("description", text(item.get("description")));
                view.put("referenceImages", listOfMaps(item.get("reference_images")).stream().map(this::imageView).toList());
                view.put("paints", listOfMaps(recipe.get("paints")).stream().map(recipePaint -> recipePaintView(recipePaint, paintsById)).toList());
                view.put("preparation", listOfMaps(recipe.get("preparation")).stream().map(this::stepView).toList());
                view.put("painting", listOfMaps(recipe.get("painting")).stream().map(this::stepView).toList());
                view.put("sources", listOfMaps(item.get("sources")).stream().map(this::sourceView).toList());
                return view;
            }).toList();
            var edition = map(game.get("edition"));
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("schemaVersion", number(game.getOrDefault("schema_version", 1)));
            view.put("id", gameId);
            view.put("name", text(game.get("name")));
            view.put("game", text(game.get("game")));
            view.put("scope", text(game.get("scope")));
            view.put("edition", Map.of("note", text(edition.get("note")), "url", text(edition.get("url"))));
            view.put("sources", listOfMaps(game.get("sources")).stream().map(this::sourceView).toList());
            view.put("items", projectItems);
            return view;
        }).sorted(Comparator.comparing(entry -> text(entry.get("name")), String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private Map<String, Object> recipePaintView(Map<String, Object> recipePaint, Map<String, Map<String, Object>> paintsById) {
        var paint = paintsById.get(text(recipePaint.get("paint_id")));
        var requested = map(recipePaint.get("requested_paint"));
        var view = new LinkedHashMap<String, Object>();
        if (paint != null) view.put("paintId", paint.get("id"));
        view.put("brand", paint == null ? text(requested.get("brand")) : paint.get("brand"));
        view.put("name", paint == null ? text(requested.get("name")) : paint.get("name"));
        view.put("role", text(recipePaint.get("role")));
        view.put("colorHex", paint == null ? defaultText(text(requested.get("color_hex")), "#777777") : paint.get("colorHex"));
        if (Boolean.TRUE.equals(recipePaint.get("pending_import"))) view.put("pendingImport", true);
        return view;
    }

    private Map<String, Object> workshopItemView(WorkshopItemState item) {
        var workflow = new LinkedHashMap<String, Object>();
        for (var stage : WorkflowStage.values()) workflow.put(stage.id(), item.workflow().get(stage).id());
        var view = new LinkedHashMap<String, Object>();
        view.put("id", item.id());
        view.put("catalogItemId", item.catalogItemId());
        view.put("projectId", item.projectId());
        view.put("displayName", item.displayName());
        view.put("workflow", workflow);
        view.put("currentStage", item.currentStage() == null ? null : item.currentStage().id());
        view.put("completed", item.completed());
        view.put("updatedAt", item.updatedAt());
        return view;
    }

    private List<Map<String, Object>> shoppingViews(DataSnapshot snapshot) {
        var priorities = Map.of("high", "haute", "medium", "moyenne", "low", "basse");
        return snapshot.shopping().stream().map(entry -> Map.<String, Object>of(
                "id", text(entry.get("id")), "brand", text(entry.get("brand")), "name", text(entry.get("name")),
                "reference", text(entry.get("reference")), "colorHex", defaultText(text(entry.get("color_hex")), "#777777"),
                "reason", text(entry.get("reason")), "priority", priorities.getOrDefault(text(entry.get("priority")), "basse"))).toList();
    }

    private Map<String, Object> sourceView(Map<String, Object> source) {
        return Map.of("kind", text(source.get("kind")), "label", text(source.get("label")), "url", text(source.get("url")));
    }

    private Map<String, Object> imageView(Map<String, Object> image) {
        var view = new LinkedHashMap<String, Object>();
        view.put("url", text(image.get("url")));
        view.put("pageUrl", text(image.get("page_url")));
        view.put("credit", text(image.get("credit")));
        if (present(text(image.get("license")))) view.put("license", text(image.get("license")));
        return view;
    }

    private Map<String, Object> stepView(Map<String, Object> step) {
        return Map.of("title", text(step.get("title")), "detail", text(step.get("detail")));
    }

    private DomainEvent idempotent(DataSnapshot snapshot, String key) {
        if (!present(key)) return null;
        return snapshot.events().stream().filter(event -> key.equals(event.idempotencyKey())).findFirst().orElse(null);
    }

    private static Object camelize(Object value) {
        if (value instanceof List<?> list) return list.stream().map(MiniPaintDexService::camelize).toList();
        if (value instanceof Map<?, ?> source) {
            var result = new LinkedHashMap<String, Object>();
            source.forEach((key, entry) -> result.put(camelKey(String.valueOf(key)), camelize(entry)));
            return result;
        }
        return value;
    }

    private static String camelKey(String value) {
        var result = new StringBuilder();
        var upper = false;
        for (var character : value.toCharArray()) {
            if (character == '_') { upper = true; continue; }
            result.append(upper ? Character.toUpperCase(character) : character);
            upper = false;
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(MiniPaintDexService::map).toList();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(MiniPaintDexService::text).filter(MiniPaintDexService::present).toList();
    }

    private static int size(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value)); } catch (NumberFormatException ignored) { return 0; }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String defaultText(String value, String fallback) {
        return present(value) ? value : fallback;
    }

    private static void require(String value, String field) {
        if (!present(value)) throw new DomainException("invalid_input", field + " is required.");
    }

    private static String csv(Object value) {
        var text = text(value);
        return text.matches(".*[,\"\\r\\n].*") ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    private static String quoted(Object value) {
        return "\"" + text(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
