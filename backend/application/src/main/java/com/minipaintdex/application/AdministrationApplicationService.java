package com.minipaintdex.application;

import com.minipaintdex.application.command.ApplyMarketPaintChangeSetCommand;
import com.minipaintdex.application.command.ApplyMarketPaintableProductChangeSetCommand;
import com.minipaintdex.application.command.ReplaceWorkshopPaintInventoryCommand;
import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.validation.StructuredDocuments;
import com.minipaintdex.application.port.MarketPaintCatalogWriter;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.PaintableProductCatalogWriter;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.WorkshopPaintInventoryWriter;
import com.minipaintdex.application.result.ApplyMarketPaintChangeSetResult;
import com.minipaintdex.application.result.ApplyMarketPaintableProductChangeSetResult;
import com.minipaintdex.application.result.ReplaceWorkshopPaintInventoryResult;
import com.minipaintdex.application.usecase.AdministrationUseCases;
import com.minipaintdex.application.validation.MarketCatalogFactory;
import com.minipaintdex.application.validation.DataSnapshotValidator;
import com.minipaintdex.application.view.RebuildProjectionResult;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.market.guide.MarketPaintingGuide;
import com.minipaintdex.domain.market.product.PaintableProduct;
import com.minipaintdex.domain.shared.DomainException;
import com.minipaintdex.domain.workshop.PaintingProjectProjector;
import com.minipaintdex.domain.workshop.WorkshopItemProjector;
import com.minipaintdex.domain.workshop.WorkshopRecipeCreated;
import com.minipaintdex.domain.workshop.WorkshopRecipeProjector;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Administrative application service for deterministic, generation-level data replacement.
 * Every command validates the complete prospective Market generation before crossing an output port.
 */
public final class AdministrationApplicationService implements AdministrationUseCases {
    private final SnapshotRepository snapshots;
    private final MarketPaintCatalogWriter marketPaints;
    private final WorkshopPaintInventoryWriter workshopPaints;
    private final PaintableProductCatalogWriter paintableProducts;

    public AdministrationApplicationService(
            SnapshotRepository snapshots,
            MarketPaintCatalogWriter marketPaints,
            WorkshopPaintInventoryWriter workshopPaints,
            PaintableProductCatalogWriter paintableProducts) {
        this.snapshots = Objects.requireNonNull(snapshots);
        this.marketPaints = Objects.requireNonNull(marketPaints);
        this.workshopPaints = Objects.requireNonNull(workshopPaints);
        this.paintableProducts = Objects.requireNonNull(paintableProducts);
    }

    @Override
    public synchronized ApplyMarketPaintChangeSetResult applyMarketPaintChangeSet(
            ApplyMarketPaintChangeSetCommand command) {
        Objects.requireNonNull(command, "command is required");
        requireEnvelope(command.schemaVersion(), command.kind(), "market_paints");
        if (command.operations().isEmpty()) throw invalid("At least one operation is required.");

        var snapshot = snapshots.load();
        var currentCatalog = MarketCatalogFactory.create(
                snapshot.marketPaints(), snapshot.paintableProducts(), snapshot.marketPaintingGuides());
        var byId = new LinkedHashMap<String, Map<String, Object>>();
        StructuredDocuments.toMaps(snapshot.marketPaints())
                .forEach(paint -> byId.put(StructuredDocuments.text(paint.get("id")), paint));
        var quantities = inventory(snapshot.paintInventory());
        var referencedPaintIds = referencedPaintIds(currentCatalog.paintingGuides(), snapshot.events());
        var operatedIds = new HashSet<String>();
        var identityMigrations = new LinkedHashMap<String, String>();
        var added = 0;
        var updated = 0;
        var rekeyed = 0;
        var retired = 0;
        var deleted = 0;
        var unchanged = 0;
        var inventoryChanged = 0;

        for (var operation : command.operations()) {
            if (operation == null) throw invalid("Operations cannot contain null entries.");
            var record = StructuredDocuments.toMap(operation.record());
            var id = required(StructuredDocuments.text(record.get("id")), "record.id");
            if (!operatedIds.add(id)) throw invalid("Only one operation is allowed per paint id: " + id);
            switch (required(operation.action(), "operation.action")) {
                case "upsert" -> {
                    var replacement = new LinkedHashMap<>(record);
                    var previous = byId.put(id, replacement);
                    if (previous == null) added++;
                    else if (previous.equals(replacement)) unchanged++;
                    else updated++;
                }
                case "rekey" -> {
                    var previousId = required(operation.previousId(), "operation.previousId");
                    if (previousId.equals(id)) throw invalid("A paint rekey must change its id: " + id);
                    if (identityMigrations.putIfAbsent(previousId, id) != null) {
                        throw invalid("Only one rekey is allowed per previous paint id: " + previousId);
                    }
                    if (byId.containsKey(id)) throw conflict("Rekey target already exists: " + id);
                    if (byId.remove(previousId) == null) throw notFound("Paint not found: " + previousId);
                    byId.put(id, new LinkedHashMap<>(record));
                    var ownedQuantity = quantities.remove(previousId);
                    if (ownedQuantity != null) {
                        if (quantities.putIfAbsent(id, ownedQuantity) != null) {
                            throw conflict("Workshop inventory already contains rekey target: " + id);
                        }
                        inventoryChanged++;
                    }
                    rekeyed++;
                }
                case "retire" -> {
                    var previous = byId.get(id);
                    if (previous == null) throw notFound("Paint not found: " + id);
                    var replacement = new LinkedHashMap<>(previous);
                    replacement.put("lifecycle_status", defaultText(
                            StructuredDocuments.text(record.get("lifecycle_status")), "discontinued"));
                    copyWhenPresent(record, replacement, "verified_at");
                    copyWhenPresent(record, replacement, "removal_reason");
                    if (previous.equals(replacement)) unchanged++;
                    else {
                        byId.put(id, replacement);
                        retired++;
                    }
                }
                case "delete" -> {
                    if (!operation.confirmedRemoval()) throw invalid("Paint deletion requires confirmedRemoval.");
                    if (quantities.getOrDefault(id, 0) > 0) {
                        throw conflict("Owned paint cannot be deleted; retire it instead: " + id);
                    }
                    if (referencedPaintIds.contains(id)) {
                        throw conflict("Referenced paint cannot be deleted; retire it instead: " + id);
                    }
                    if (byId.remove(id) == null) throw notFound("Paint not found: " + id);
                    deleted++;
                }
                default -> throw invalid("Unsupported paint operation: " + operation.action());
            }
            if (operation.workshopQuantityDelta() < 0) {
                throw invalid("workshopQuantityDelta cannot be negative.");
            }
            if ("rekey".equals(operation.action()) && operation.workshopQuantityDelta() != 0) {
                throw invalid("A paint rekey cannot change workshop quantity.");
            }
            if (operation.workshopQuantityDelta() > 0 && !"delete".equals(operation.action())) {
                quantities.compute(id, (ignored, quantity) -> Math.addExact(
                        quantity == null ? 0 : quantity, operation.workshopQuantityDelta()));
                inventoryChanged++;
            }
        }

        var result = byId.values().stream()
                .sorted(Comparator.comparing(paint -> StructuredDocuments.text(paint.get("id"))))
                .map(paint -> java.util.Collections.unmodifiableMap(new LinkedHashMap<>(paint))).toList();
        var resultDocuments = StructuredDocuments.fromMaps(result);
        var normalizedInventory = inventoryDocuments(quantities);
        var rewrittenGuides = rewritePaintReferences(snapshot.marketPaintingGuides(), identityMigrations);
        var rewrittenShopping = rewritePaintReferences(snapshot.shopping(), identityMigrations);
        if (!identityMigrations.isEmpty()) {
            var immutableReferences = referencedPaintIds(List.of(), snapshot.events());
            var blocked = identityMigrations.keySet().stream().filter(immutableReferences::contains).sorted().toList();
            if (!blocked.isEmpty()) {
                throw conflict("Immutable workshop events reference paint ids that cannot be rekeyed: " + blocked);
            }
        }
        DataSnapshotValidator.validate(new DataSnapshot(
                snapshot.site(), resultDocuments, normalizedInventory, snapshot.paintableProducts(),
                rewrittenGuides, rewrittenShopping, snapshot.events()));
        if (!command.dryRun()) {
            if (!identityMigrations.isEmpty()) {
                marketPaints.replaceMarketPaintIdentities(
                        resultDocuments, normalizedInventory, rewrittenGuides, rewrittenShopping);
            } else if (inventoryChanged > 0) {
                marketPaints.replaceMarketPaintsAndWorkshopInventory(
                        resultDocuments, normalizedInventory, workshopPaints);
            } else {
                marketPaints.replaceMarketPaints(resultDocuments);
            }
        }
        return new ApplyMarketPaintChangeSetResult(
                added, updated, rekeyed, retired, deleted, unchanged,
                inventoryChanged, result.size(), !command.dryRun());
    }

    @Override
    public synchronized ReplaceWorkshopPaintInventoryResult replaceWorkshopPaintInventory(
            ReplaceWorkshopPaintInventoryCommand command) {
        Objects.requireNonNull(command, "command is required");
        requireEnvelope(command.schemaVersion(), command.kind(), "workshop_paints");
        var snapshot = snapshots.load();
        var marketIds = MarketCatalogFactory.create(
                snapshot.marketPaints(), snapshot.paintableProducts(), snapshot.marketPaintingGuides())
                .paints().stream().map(paint -> paint.id()).collect(java.util.stream.Collectors.toSet());
        var inventory = new LinkedHashMap<String, Integer>();
        for (var entry : command.paints()) {
            if (entry == null) throw invalid("Paint inventory cannot contain null entries.");
            var paintId = required(entry.paintId(), "paints.paintId");
            if (!marketIds.contains(paintId)) throw notFound("Market paint not found: " + paintId);
            if (entry.quantity() < 0) throw invalid("Paint quantity cannot be negative: " + paintId);
            if (inventory.putIfAbsent(paintId, entry.quantity()) != null) {
                throw invalid("Duplicate workshop paint: " + paintId);
            }
        }
        var normalized = inventoryDocuments(inventory);
        if (!command.dryRun()) workshopPaints.replaceWorkshopPaints(normalized);
        return new ReplaceWorkshopPaintInventoryResult(
                normalized.size(), inventory.values().stream().mapToInt(Integer::intValue).sum(), !command.dryRun());
    }

    @Override
    public synchronized ApplyMarketPaintableProductChangeSetResult applyMarketPaintableProductChangeSet(
            ApplyMarketPaintableProductChangeSetCommand command) {
        Objects.requireNonNull(command, "command is required");
        requireEnvelope(command.schemaVersion(), command.kind(), "market_product");
        var snapshot = snapshots.load();
        var product = MarketCatalogFactory.product(command.product());
        var products = new java.util.ArrayList<>(snapshot.paintableProducts());
        var previous = products.stream().filter(candidate -> product.id().equals(candidate.id())).findFirst().orElse(null);
        products.removeIf(candidate -> product.id().equals(candidate.id()));
        products.add(product);

        var replacedCatalogItems = previous == null ? Set.<String>of() : previous.catalogItems().stream()
                .map(PaintableProduct.CatalogItem::id).collect(java.util.stream.Collectors.toSet());
        var guides = new java.util.ArrayList<>(snapshot.marketPaintingGuides().stream()
                .filter(document -> !replacedCatalogItems.contains(StructuredDocuments.text(
                        StructuredDocuments.toMap(document).get("catalog_item_id"))))
                .toList());
        guides.addAll(command.paintingGuides());
        MarketCatalogFactory.create(snapshot.marketPaints(), products, guides);

        if (!command.dryRun()) {
            paintableProducts.replaceProduct(product.id(), command.product(), command.paintingGuides());
        }
        return new ApplyMarketPaintableProductChangeSetResult(
                product.id(), product.catalogItems().size(), command.paintingGuides().size(), !command.dryRun());
    }

    @Override
    public synchronized RebuildProjectionResult rebuildProjections() {
        var snapshot = snapshots.load();
        var catalog = MarketCatalogFactory.create(
                snapshot.marketPaints(), snapshot.paintableProducts(), snapshot.marketPaintingGuides());
        return new RebuildProjectionResult(
                "rebuilt", "in_memory", snapshot.events().size(), Instant.now(), catalog.paints().size(),
                catalog.paintableProducts().size(), PaintingProjectProjector.project(snapshot.events()).size(),
                WorkshopItemProjector.project(snapshot.events()).size(), catalog.paintingGuides().size(),
                WorkshopRecipeProjector.project(snapshot.events()).size());
    }

    private static LinkedHashMap<String, Integer> inventory(List<StructuredDocument> documents) {
        var quantities = new LinkedHashMap<String, Integer>();
        for (var entry : StructuredDocuments.toMaps(documents)) {
            var paintId = required(StructuredDocuments.text(entry.get("paint_id")), "paint_inventory.paint_id");
            var quantity = StructuredDocuments.integer(entry.get("quantity"), "paint_inventory.quantity");
            if (quantity < 0) throw invalid("Paint quantity cannot be negative: " + paintId);
            if (quantities.putIfAbsent(paintId, quantity) != null) {
                throw invalid("Duplicate workshop paint: " + paintId);
            }
        }
        return quantities;
    }

    private static List<StructuredDocument> inventoryDocuments(Map<String, Integer> quantities) {
        return StructuredDocuments.fromMaps(quantities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.<String, Object>of("paint_id", entry.getKey(), "quantity", entry.getValue()))
                .toList());
    }

    private static List<StructuredDocument> rewritePaintReferences(
            List<StructuredDocument> documents,
            Map<String, String> identities) {
        if (identities.isEmpty()) return documents;
        return StructuredDocuments.fromMaps(StructuredDocuments.toMaps(documents).stream()
                .map(document -> rewritePaintReferences(document, identities)).toList());
    }

    private static Map<String, Object> rewritePaintReferences(
            Map<String, Object> source,
            Map<String, String> identities) {
        var result = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> {
            if (("paint_id".equals(key) || "market_paint_id".equals(key)) && value instanceof String id) {
                result.put(key, identities.getOrDefault(id, id));
            } else {
                result.put(key, rewritePaintReferenceValue(value, identities));
            }
        });
        return result;
    }

    private static Object rewritePaintReferenceValue(Object value, Map<String, String> identities) {
        if (value instanceof Map<?, ?> values) {
            var normalized = new LinkedHashMap<String, Object>();
            values.forEach((key, entry) -> normalized.put(String.valueOf(key), entry));
            return rewritePaintReferences(normalized, identities);
        }
        if (value instanceof List<?> values) {
            return values.stream().map(entry -> rewritePaintReferenceValue(entry, identities)).toList();
        }
        return value;
    }

    private static Set<String> referencedPaintIds(
            List<MarketPaintingGuide> guides,
            List<EventEnvelope> events) {
        var result = new java.util.LinkedHashSet<String>();
        guides.stream().flatMap(guide -> guide.slots().stream())
                .map(slot -> slot.marketPaintId()).filter(Objects::nonNull).forEach(result::add);
        events.stream().map(EventEnvelope::event).filter(WorkshopRecipeCreated.class::isInstance)
                .map(WorkshopRecipeCreated.class::cast).flatMap(event -> event.solutions().stream())
                .flatMap(solution -> solution.referencedPaintIds().stream()).forEach(result::add);
        return Set.copyOf(result);
    }

    private static void requireEnvelope(int schemaVersion, String kind, String expectedKind) {
        if (schemaVersion != 1) throw invalid("schemaVersion must be 1.");
        if (!expectedKind.equals(kind)) throw invalid("kind must be " + expectedKind + ".");
    }

    private static void copyWhenPresent(Map<String, Object> source, Map<String, Object> target, String field) {
        if (!StructuredDocuments.text(source.get(field)).isBlank()) target.put(field, source.get(field));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " is required.");
        return value.trim();
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static DomainException invalid(String message) {
        return new DomainException("invalid_input", message);
    }

    private static DomainException notFound(String message) {
        return new DomainException("not_found", message);
    }

    private static DomainException conflict(String message) {
        return new DomainException("conflict", message);
    }
}
