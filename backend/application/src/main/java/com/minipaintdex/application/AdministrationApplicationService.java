package com.minipaintdex.application;

import com.minipaintdex.application.command.ApplyPaintProductChangeSetCommand;
import com.minipaintdex.application.command.ApplyMarketPaintableProductChangeSetCommand;
import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.validation.StructuredDocuments;
import com.minipaintdex.application.port.PaintProductCatalogWriter;
import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.PaintableProductCatalogWriter;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.result.ApplyPaintProductChangeSetResult;
import com.minipaintdex.application.result.ApplyMarketPaintableProductChangeSetResult;
import com.minipaintdex.application.usecase.AdministrationUseCases;
import com.minipaintdex.application.validation.MarketCatalogFactory;
import com.minipaintdex.application.validation.DataSnapshotValidator;
import com.minipaintdex.application.validation.PaintProductImportEvidence;
import com.minipaintdex.application.view.RebuildProjectionResult;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.market.guide.MarketPaintingGuide;
import com.minipaintdex.domain.market.product.PaintableProduct;
import com.minipaintdex.domain.shared.DomainException;
import com.minipaintdex.domain.workshop.PaintingProjectProjector;
import com.minipaintdex.domain.workshop.WorkshopPaintableProjector;
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
    private final PaintProductCatalogWriter paintProducts;
    private final PaintableProductCatalogWriter paintableProducts;
    private final com.minipaintdex.application.port.RackCatalogWriter rackCatalog;
    private final RackReferenceWriteScope rackWrites;

    public AdministrationApplicationService(
            SnapshotRepository snapshots,
            PaintProductCatalogWriter paintProducts,
            PaintableProductCatalogWriter paintableProducts, com.minipaintdex.application.port.RackCatalogWriter rackCatalog, RackReferenceWriteScope rackWrites) {
        this.rackWrites = Objects.requireNonNull(rackWrites);
        this.rackCatalog = Objects.requireNonNull(rackCatalog);
        this.snapshots = Objects.requireNonNull(snapshots);
        this.paintProducts = Objects.requireNonNull(paintProducts);
        this.paintableProducts = Objects.requireNonNull(paintableProducts);
    }
    @Override public synchronized long saveRackReference(com.minipaintdex.application.command.SaveRackReferenceCommand command) {
        return rackWrites.run(() -> saveRackReferenceWithinScope(command));
    }
    private long saveRackReferenceWithinScope(com.minipaintdex.application.command.SaveRackReferenceCommand command) {
        var current = snapshots.load().rackCatalog();
        if ((command.containerFormat() != null && current.containerFormats().contains(command.containerFormat()))
                || (command.rackProduct() != null && current.rackProducts().contains(command.rackProduct()))) return current.revision();
        var formats = new java.util.ArrayList<>(current.containerFormats());
        var racks = new java.util.ArrayList<>(current.rackProducts());
        if (command.containerFormat() != null) { formats.removeIf(value -> value.id().equals(command.containerFormat().id())); formats.add(command.containerFormat()); }
        else { racks.removeIf(value -> value.id().equals(command.rackProduct().id())); racks.add(command.rackProduct()); }
        var next = new com.minipaintdex.domain.market.storage.RackCatalog(1, current.revision() + 1, formats, racks);
        current.validateReplacement(next);
        if (current.revision() != command.expectedRevision()) throw conflict("Rack catalog revision changed.");
        if (command.dryRun()) return current.revision();
        return rackCatalog.replace(next, command.expectedRevision()).revision();
    }

    @Override
    public synchronized ApplyPaintProductChangeSetResult applyPaintProductChangeSet(
            ApplyPaintProductChangeSetCommand command) {
        return rackWrites.run(() -> applyPaintProductChangeSetWithinScope(command));
    }
    private ApplyPaintProductChangeSetResult applyPaintProductChangeSetWithinScope(ApplyPaintProductChangeSetCommand command) {
        Objects.requireNonNull(command, "command is required");
        requireEnvelope(command.schemaVersion(), command.kind(), "market_paints");
        if (command.operations().isEmpty() && command.catalogEditions().isEmpty() && command.paintUsageGuides().isEmpty() && command.containerFormats().isEmpty()) throw invalid("At least one market entry is required.");

        var snapshot = snapshots.load();
        var containers = new LinkedHashMap<String, com.minipaintdex.domain.market.storage.PaintContainerFormat>();
        snapshot.rackCatalog().containerFormats().forEach(format -> containers.put(format.id(), format));
        var operatedContainers = new HashSet<String>();
        for (var document : command.containerFormats()) {
            var format = MarketCatalogFactory.containerFormat(document);
            if (!operatedContainers.add(format.id())) throw invalid("Duplicate container format: " + format.id());
            containers.put(format.id(), format);
        }
        var containerValues = List.copyOf(containers.values());
        var nextRackCatalog = containerValues.equals(snapshot.rackCatalog().containerFormats()) ? snapshot.rackCatalog()
                : new com.minipaintdex.domain.market.storage.RackCatalog(1, snapshot.rackCatalog().revision() + 1, containerValues, snapshot.rackCatalog().rackProducts());
        var currentCatalog = MarketCatalogFactory.create(
                snapshot.paintProducts(), snapshot.paintableProducts(), snapshot.marketPaintingGuides(), snapshot.paintCatalogEditions(), snapshot.paintUsageGuides());
        var byId = new LinkedHashMap<String, Map<String, Object>>();
        StructuredDocuments.toMaps(snapshot.paintProducts())
                .forEach(paint -> byId.put(StructuredDocuments.text(paint.get("id")), paint));
        var quantities = snapshot.paintInventory().stocks().stream().collect(java.util.stream.Collectors.toMap(
                com.minipaintdex.domain.workshop.WorkshopPaintStock::paintProductId, com.minipaintdex.domain.workshop.WorkshopPaintStock::quantity));
        var editions = new LinkedHashMap<String, StructuredDocument>();
        snapshot.paintCatalogEditions().forEach(document -> editions.put(MarketCatalogFactory.catalogEdition(document).id(), document));
        var updatedEditionIds = new HashSet<String>();
        for (var document : command.catalogEditions()) {
            var edition = MarketCatalogFactory.catalogEdition(document);
            if (!updatedEditionIds.add(edition.id())) throw invalid("Duplicate catalog edition: " + edition.id());
            var previous = editions.get(edition.id());
            if (previous != null && !MarketCatalogFactory.catalogEdition(previous).brand().equals(edition.brand())) {
                throw conflict("A catalog edition cannot change brand: " + edition.id());
            }
            editions.put(edition.id(), document);
        }
        var editionDocuments = List.copyOf(editions.values());
        var usageGuides = new LinkedHashMap<String, StructuredDocument>();
        snapshot.paintUsageGuides().forEach(document -> usageGuides.put(MarketCatalogFactory.paintUsageGuide(document).id(), document));
        var operatedGuideIds = new HashSet<String>();
        for (var document : command.paintUsageGuides()) {
            var guide = MarketCatalogFactory.paintUsageGuide(document);
            if (!operatedGuideIds.add(guide.id())) throw invalid("Duplicate usage guide: " + guide.id());
            var previous = usageGuides.get(guide.id());
            if (previous != null) MarketCatalogFactory.paintUsageGuide(previous).validateReplacement(guide);
            else if (guide.revision() != 1) throw invalid("A new usage guide starts at revision 1");
            usageGuides.put(guide.id(), document);
        }
        var usageGuideDocuments = List.copyOf(usageGuides.values());
        var referencedPaintIds = new HashSet<>(referencedPaintIds(currentCatalog.paintingGuides(), snapshot.events()));
        com.minipaintdex.domain.workshop.PaintPotProjector.project(snapshot.events()).forEach(pot -> referencedPaintIds.add(pot.paintProductId()));
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
                    var previous = byId.get(id);
                    var replacement = previous == null ? record : StructuredDocuments.toMap(
                            PaintProductImportEvidence.preserve(StructuredDocuments.fromMap(previous), operation.record()));
                    byId.put(id, replacement);
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
                    var previous = byId.remove(previousId);
                    if (previous == null) throw notFound("Paint not found: " + previousId);
                    byId.put(id, StructuredDocuments.toMap(PaintProductImportEvidence.preserve(
                            StructuredDocuments.fromMap(previous), operation.record())));
                    if (referencedPaintIds(List.of(), snapshot.events()).contains(previousId)
                            || com.minipaintdex.domain.workshop.PaintPotProjector.project(snapshot.events()).stream().anyMatch(pot -> pot.paintProductId().equals(previousId))) throw conflict("A product referenced by pot or recipe history cannot be rekeyed: " + previousId);
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
            if (operation.workshopQuantityDelta() != 0) throw invalid("Catalog changes cannot change pot quantities; use paint-pots import.");
        }

        var result = byId.values().stream()
                .sorted(Comparator.comparing(paint -> StructuredDocuments.text(paint.get("id"))))
                .map(paint -> java.util.Collections.unmodifiableMap(new LinkedHashMap<>(paint))).toList();
        var resultDocuments = StructuredDocuments.fromMaps(result);
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
                snapshot.site(), resultDocuments, snapshot.paintableProducts(),
                rewrittenGuides, rewrittenShopping, snapshot.events(), editionDocuments, usageGuideDocuments, nextRackCatalog));
        if (!identityMigrations.isEmpty() && !command.containerFormats().isEmpty()) throw invalid("Container updates cannot be combined with paint identity rekeys.");
        if ((!command.catalogEditions().isEmpty() || !command.paintUsageGuides().isEmpty()) && (!identityMigrations.isEmpty() || inventoryChanged > 0)) {
            throw invalid("Catalog editions are market knowledge: apply their updates separately from inventory changes or rekeys.");
        }
        if (!command.dryRun()) {
            if (!identityMigrations.isEmpty()) {
                paintProducts.replacePaintProductIdentities(
                        resultDocuments, rewrittenGuides, rewrittenShopping);
            } else {
                paintProducts.replacePaintProductCatalog(resultDocuments, editionDocuments, usageGuideDocuments, nextRackCatalog);
            }
        }
        return new ApplyPaintProductChangeSetResult(
                added, updated, rekeyed, retired, deleted, unchanged,
                inventoryChanged, result.size(), !command.dryRun());
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

        var replacedPaintableComponents = previous == null ? Set.<String>of() : previous.paintableComponents().stream()
                .map(PaintableProduct.PaintableComponent::id).collect(java.util.stream.Collectors.toSet());
        var guides = new java.util.ArrayList<>(snapshot.marketPaintingGuides().stream()
                .filter(document -> !replacedPaintableComponents.contains(StructuredDocuments.text(
                        StructuredDocuments.toMap(document).get("catalog_item_id"))))
                .toList());
        guides.addAll(command.paintingGuides());
        MarketCatalogFactory.create(snapshot.paintProducts(), products, guides, snapshot.paintCatalogEditions(), snapshot.paintUsageGuides());

        if (!command.dryRun()) {
            paintableProducts.replaceProduct(product.id(), command.product(), command.paintingGuides());
        }
        return new ApplyMarketPaintableProductChangeSetResult(
                product.id(), product.paintableComponents().size(), command.paintingGuides().size(), !command.dryRun());
    }

    @Override
    public synchronized RebuildProjectionResult rebuildProjections() {
        var snapshot = snapshots.load();
        var catalog = MarketCatalogFactory.create(
                snapshot.paintProducts(), snapshot.paintableProducts(), snapshot.marketPaintingGuides(), snapshot.paintCatalogEditions(), snapshot.paintUsageGuides());
        return new RebuildProjectionResult(
                "rebuilt", "in_memory", snapshot.events().size(), Instant.now(), catalog.paints().size(),
                catalog.paintableProducts().size(), PaintingProjectProjector.project(snapshot.events()).size(),
                WorkshopPaintableProjector.project(snapshot.events()).size(), catalog.paintingGuides().size(),
                WorkshopRecipeProjector.project(snapshot.events()).size());
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
                .map(slot -> slot.paintProductId()).filter(Objects::nonNull).forEach(result::add);
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
