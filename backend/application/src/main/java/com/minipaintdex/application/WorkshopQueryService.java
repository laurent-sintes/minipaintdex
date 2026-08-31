package com.minipaintdex.application;

import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.validation.MarketCatalogFactory;
import com.minipaintdex.application.validation.StructuredDocuments;
import com.minipaintdex.application.view.GuideReconciliationView;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.MarketPaintingGuideView;
import com.minipaintdex.application.view.MissingPaintView;
import com.minipaintdex.application.view.PaintableProductSummaryView;
import com.minipaintdex.application.view.PaintableProductView;
import com.minipaintdex.application.view.PaintingProjectView;
import com.minipaintdex.application.view.ProductImportPreviewView;
import com.minipaintdex.application.view.ShoppingItemView;
import com.minipaintdex.application.view.WorkshopItemView;
import com.minipaintdex.application.view.WorkshopOverviewView;
import com.minipaintdex.application.view.WorkshopRecipeView;
import com.minipaintdex.domain.event.EventEnvelope;
import com.minipaintdex.domain.market.guide.MarketPaintingGuide;
import com.minipaintdex.domain.market.paint.MarketPaint;
import com.minipaintdex.domain.market.paint.PaintMatchEngine;
import com.minipaintdex.domain.market.product.PaintableProduct;
import com.minipaintdex.domain.shared.DomainException;
import com.minipaintdex.domain.workshop.PaintingProjectProjector;
import com.minipaintdex.domain.workshop.ShoppingItemStatusChanged;
import com.minipaintdex.domain.workshop.WorkflowStage;
import com.minipaintdex.domain.workshop.WorkflowStageStatus;
import com.minipaintdex.domain.workshop.Workshop;
import com.minipaintdex.domain.workshop.WorkshopItemProjector;
import com.minipaintdex.domain.workshop.WorkshopItemState;
import com.minipaintdex.domain.workshop.WorkshopProjector;
import com.minipaintdex.domain.workshop.WorkshopRecipeProjector;
import com.minipaintdex.domain.workshop.WorkshopRecipeState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds Workshop read models without owning command decisions or persistence mutations. */
public final class WorkshopQueryService {
    private final SnapshotRepository snapshots;
    private final PaintMatchEngine paintMatchEngine;
    private final MarketPaintQueryService paintQueries;

    public WorkshopQueryService(SnapshotRepository snapshots, PaintMatchEngine paintMatchEngine) {
        this.snapshots = Objects.requireNonNull(snapshots);
        this.paintMatchEngine = Objects.requireNonNull(paintMatchEngine);
        this.paintQueries = new MarketPaintQueryService(() -> marketCatalog(this.snapshots.load()));
    }

    public WorkshopOverviewView workshopOverview() {
        var snapshot = snapshots.load();
        return workshopView(snapshot, WorkshopItemProjector.project(snapshot.events()));
    }

    public List<PaintingProjectView> listPaintingProjects() { return workshopOverview().paintingProjects(); }

    public List<WorkshopItemView> listWorkshopItems(String paintingProjectId) {
        var items = WorkshopItemProjector.project(snapshots.load().events()).stream()
                .map(this::workshopItemView).toList();
        return !present(paintingProjectId) ? items : items.stream()
                .filter(item -> paintingProjectId.equals(item.paintingProjectId())).toList();
    }

    public List<ShoppingItemView> listShoppingItems() { return shoppingViews(snapshots.load()); }

    public WorkshopItemView.Detail getWorkshopItem(String itemId) {
        require(itemId, "itemId");
        var snapshot = snapshots.load();
        var item = WorkshopItemProjector.project(snapshot.events()).stream()
                .filter(candidate -> itemId.equals(candidate.id())).findFirst()
                .orElseThrow(() -> notFound("Workshop item not found: " + itemId));
        return new WorkshopItemView.Detail(workshopItemView(item), snapshot.events().stream()
                .filter(event -> itemId.equals(event.aggregateId()))
                .sorted(Comparator.comparing(EventEnvelope::recordedAt).reversed()).toList());
    }

    public ProductImportPreviewView previewProductImport(String productId) {
        var snapshot = snapshots.load();
        return importPreview(snapshot, findProduct(snapshot, productId));
    }

    public GuideReconciliationView reconcileMarketPaintingGuide(String guideId) {
        require(guideId, "guideId");
        var snapshot = snapshots.load();
        var catalog = marketCatalog(snapshot);
        var guide = catalog.paintingGuides().stream().filter(candidate -> guideId.equals(candidate.id()))
                .findFirst().orElseThrow(() -> notFound("Market painting guide not found: " + guideId));
        var paintsById = catalog.paints().stream().collect(Collectors.toMap(MarketPaint::id, Function.identity()));
        var ownedIds = StructuredDocuments.toMaps(snapshot.paintInventory()).stream()
                .filter(entry -> StructuredDocuments.integer(entry.get("quantity"), "paint_inventory.quantity") > 0)
                .map(entry -> StructuredDocuments.text(entry.get("paint_id"))).collect(Collectors.toSet());
        var ownedProfiles = ownedIds.stream().map(paintsById::get).filter(Objects::nonNull)
                .map(this::paintProfile).toList();
        var paintViewsById = paintQueries.views(catalog).stream()
                .collect(Collectors.toMap(MarketPaintView::id, Function.identity()));
        var slots = guide.slots().stream().map(slot -> {
            var source = paintsById.get(slot.marketPaintId());
            var candidates = source == null ? List.<GuideReconciliationView.PaintMatchView>of()
                    : paintMatchEngine.rank(paintProfile(source), ownedProfiles).stream().map(match ->
                    new GuideReconciliationView.PaintMatchView(
                            paintViewsById.get(match.candidatePaintId()), match.score(), match.deltaE2000(),
                            match.requiresManualReview(), match.strategy(), new GuideReconciliationView.DimensionsView(
                            match.colorScore(), match.functionalTypeScore(), match.behaviorScore(),
                            match.finishScore(), match.opacityScore(), match.mediumScore()), match.reasons())).toList();
            return new GuideReconciliationView.SlotReconciliationView(
                    guideSlot(slot), paintViewsById.get(slot.marketPaintId()), candidates,
                    source != null && paintMatchEngine.requiresManualReview(paintProfile(source)));
        }).toList();
        return new GuideReconciliationView(guideView(guide), slots, ownedProfiles.size());
    }

    public List<WorkshopRecipeView> listWorkshopRecipes(String catalogItemId) {
        return WorkshopRecipeProjector.project(snapshots.load().events()).stream()
                .filter(recipe -> !present(catalogItemId) || catalogItemId.equals(recipe.catalogItemId()))
                .map(WorkshopQueryService::recipeView).sorted(Comparator.comparing(WorkshopRecipeView::id)).toList();
    }

    public List<EventEnvelope> listActivity(String paintingProjectId) {
        var snapshot = snapshots.load();
        var itemIds = WorkshopItemProjector.project(snapshot.events()).stream()
                .filter(item -> !present(paintingProjectId) || paintingProjectId.equals(item.paintingProjectId()))
                .map(WorkshopItemState::id).collect(Collectors.toSet());
        return snapshot.events().stream().filter(event -> !present(paintingProjectId)
                        || paintingProjectId.equals(event.projectId()) || itemIds.contains(event.aggregateId()))
                .sorted(Comparator.comparing(EventEnvelope::recordedAt).reversed()).toList();
    }

    PaintableProduct findProduct(DataSnapshot snapshot, String productId) {
        require(productId, "productId");
        return snapshot.paintableProducts().stream().filter(product -> productId.equals(product.id())).findFirst()
                .orElseThrow(() -> notFound("Paintable product not found: " + productId));
    }

    MarketCatalogSnapshot marketCatalog(DataSnapshot snapshot) {
        return MarketCatalogFactory.create(
                snapshot.marketPaints(), snapshot.paintableProducts(), snapshot.marketPaintingGuides());
    }

    List<ShoppingItemView> shoppingViews(DataSnapshot snapshot) {
        var paintsById = marketCatalog(snapshot).paints().stream()
                .collect(Collectors.toMap(MarketPaint::id, Function.identity()));
        var productsById = snapshot.paintableProducts().stream()
                .collect(Collectors.toMap(PaintableProduct::id, PaintableProduct::name));
        var requiredByPaint = new LinkedHashMap<String, java.util.LinkedHashSet<String>>();
        for (var project : PaintingProjectProjector.project(snapshot.events())) {
            var product = snapshot.paintableProducts().stream()
                    .filter(candidate -> project.paintableProductId().equals(candidate.id())).findFirst().orElse(null);
            if (product == null) continue;
            for (var missing : importPreview(snapshot, product).missingPaints()) {
                requiredByPaint.computeIfAbsent(missing.id(), ignored -> new java.util.LinkedHashSet<>())
                        .add(product.id());
            }
        }
        var checkedById = new LinkedHashMap<String, Boolean>();
        snapshot.events().stream().filter(event -> "shopping_item.status_changed".equals(event.eventType()))
                .sorted(Comparator.comparing(EventEnvelope::recordedAt).thenComparing(EventEnvelope::eventId))
                .map(EventEnvelope::event).filter(ShoppingItemStatusChanged.class::isInstance)
                .map(ShoppingItemStatusChanged.class::cast)
                .forEach(event -> checkedById.put(event.aggregateId(), event.checked()));
        var plannedPaintIds = new java.util.HashSet<String>();
        var result = new ArrayList<ShoppingItemView>();
        for (var entry : StructuredDocuments.toMaps(snapshot.shopping())) {
            var paintId = text(entry.get("market_paint_id"));
            var paint = paintsById.get(paintId);
            var sourceProducts = requiredByPaint.getOrDefault(paintId, new java.util.LinkedHashSet<>());
            result.add(new ShoppingItemView(
                    text(entry.get("id")), paint == null ? text(entry.get("brand")) : paint.brand(),
                    paint == null ? text(entry.get("name")) : paint.name(),
                    paint == null ? text(entry.get("reference")) : text(paint.reference()),
                    paint == null ? text(entry.get("color_hex")) : text(paint.color().hex()),
                    text(entry.get("reason")), defaultText(text(entry.get("priority")), "low"),
                    sourceProducts.isEmpty() ? "planned" : "required", true, paintId, List.copyOf(sourceProducts),
                    sourceProducts.stream().map(id -> productsById.getOrDefault(id, id)).toList(),
                    checkedById.getOrDefault(text(entry.get("id")), false)));
            if (present(paintId)) plannedPaintIds.add(paintId);
        }
        requiredByPaint.forEach((paintId, sourceProducts) -> {
            if (plannedPaintIds.contains(paintId)) return;
            var paint = paintsById.get(paintId);
            result.add(new ShoppingItemView(
                    "required-" + paintId, paint == null ? "" : paint.brand(), paint == null ? "" : paint.name(),
                    paint == null ? "" : text(paint.reference()), paint == null ? "" : text(paint.color().hex()),
                    "", "high", "required", false, paintId, List.copyOf(sourceProducts),
                    sourceProducts.stream().map(id -> productsById.getOrDefault(id, id)).toList(),
                    checkedById.getOrDefault("required-" + paintId, false)));
        });
        return List.copyOf(result);
    }

    private ProductImportPreviewView importPreview(DataSnapshot snapshot, PaintableProduct product) {
        var catalog = marketCatalog(snapshot);
        var catalogIds = product.catalogItems().stream().map(PaintableProduct.CatalogItem::id).collect(Collectors.toSet());
        var guides = catalog.paintingGuides().stream().filter(guide -> catalogIds.contains(guide.catalogItemId())).toList();
        var requiredPaintIds = new java.util.LinkedHashSet<String>();
        var pendingSlots = 0;
        for (var guide : guides) for (var slot : guide.slots()) {
            if (present(slot.marketPaintId())) requiredPaintIds.add(slot.marketPaintId());
            else if (slot.pendingImport()) pendingSlots++;
        }
        var ownedPaintIds = StructuredDocuments.toMaps(snapshot.paintInventory()).stream()
                .filter(entry -> StructuredDocuments.integer(entry.get("quantity"), "paint_inventory.quantity") > 0)
                .map(entry -> StructuredDocuments.text(entry.get("paint_id"))).collect(Collectors.toSet());
        var paintsById = catalog.paints().stream().collect(Collectors.toMap(MarketPaint::id, Function.identity()));
        var missing = requiredPaintIds.stream().filter(id -> !ownedPaintIds.contains(id)).map(id -> {
            var paint = paintsById.get(id);
            return paint == null ? new MissingPaintView(id, "", "", "")
                    : new MissingPaintView(id, paint.name(), paint.brand(), text(paint.reference()));
        }).toList();
        return new ProductImportPreviewView(
                product.id(), product.name(), product.catalogItems().size(), product.expectedPaintableCount(),
                guides.size(), requiredPaintIds.size(), missing.size(), missing, pendingSlots,
                PaintingProjectProjector.project(snapshot.events()).stream()
                        .anyMatch(project -> product.id().equals(project.paintableProductId())));
    }

    private WorkshopOverviewView workshopView(DataSnapshot snapshot, List<WorkshopItemState> items) {
        var marketById = snapshot.paintableProducts().stream().map(product -> new PaintableProductSummaryView(
                        product.id(), product.name(), product.line(), product.productType(), product.scope(),
                        product.catalogItems().size(), product.expectedPaintableCount()))
                .collect(Collectors.toMap(PaintableProductSummaryView::id, Function.identity()));
        var projects = PaintingProjectProjector.project(snapshot.events()).stream().map(project -> {
            var market = marketById.get(project.paintableProductId());
            if (market == null) return new PaintingProjectView(
                    project.id(), project.paintableProductId(), project.name(), project.status().id(),
                    project.createdAt(), project.updatedAt(), project.createdAt(),
                    0, 0, 0, 0, 0, 0, 0, List.of(), 0, true);
            var projectItems = items.stream().filter(item -> project.id().equals(item.paintingProjectId())).toList();
            var completed = (int) projectItems.stream().filter(WorkshopItemState::completed).count();
            var inProgress = (int) projectItems.stream().filter(item -> !item.completed()
                    && item.workflow().values().stream().anyMatch(status -> status != WorkflowStageStatus.PENDING)).count();
            var stageUnits = projectItems.size() * WorkflowStage.values().length;
            var finishedStages = projectItems.stream().mapToInt(item -> (int) item.workflow().values().stream()
                    .filter(status -> status == WorkflowStageStatus.COMPLETED || status == WorkflowStageStatus.SKIPPED)
                    .count()).sum();
            var preview = importPreview(snapshot, findProduct(snapshot, project.paintableProductId()));
            return new PaintingProjectView(
                    project.id(), project.paintableProductId(), market.name(), project.status().id(),
                    project.createdAt(), project.updatedAt(), project.createdAt(), projectItems.size(), completed,
                    inProgress, projectItems.size() - completed - inProgress,
                    stageUnits == 0 ? 0 : Math.round(finishedStages * 100f / stageUnits), preview.requiredPaintCount(),
                    preview.missingPaintCount(), preview.missingPaints(), preview.pendingPaintSlotCount(), false);
        }).toList();
        var completed = (int) items.stream().filter(WorkshopItemState::completed).count();
        return new WorkshopOverviewView(
                WorkshopProjector.project(snapshot.events()).map(Workshop::id).orElse(Workshop.DEFAULT_ID),
                projects, projects.size(), items.size(), completed,
                items.isEmpty() ? 0 : Math.round(completed * 100f / items.size()),
                snapshot.events().stream().sorted(Comparator.comparing(EventEnvelope::recordedAt).reversed())
                        .limit(12).toList());
    }

    private WorkshopItemView workshopItemView(WorkshopItemState item) {
        var workflow = new WorkshopItemView.WorkflowView(
                item.workflow().get(WorkflowStage.PREPARATION).id(), item.workflow().get(WorkflowStage.PRIMING).id(),
                item.workflow().get(WorkflowStage.PRE_HIGHLIGHT).id(), item.workflow().get(WorkflowStage.PAINTING).id(),
                item.workflow().get(WorkflowStage.FINISHING).id(), item.workflow().get(WorkflowStage.BASING).id());
        return new WorkshopItemView(
                item.id(), item.catalogItemId(), item.paintingProjectId(), item.displayName(), workflow,
                item.currentStage() == null ? null : item.currentStage().id(), item.completed(),
                defaultText(item.recipeId(), ""), item.recipeVersion(), item.updatedAt());
    }

    private PaintMatchEngine.Paint paintProfile(MarketPaint paint) {
        var behavior = new java.util.LinkedHashSet<>(paint.behaviorTags());
        behavior.addAll(paint.tags());
        return new PaintMatchEngine.Paint(
                paint.id(), text(paint.color().hex()), paint.functionalType().id(), text(paint.finish()),
                text(paint.opacity()), text(paint.medium()), Set.copyOf(behavior));
    }

    private static MarketPaintingGuideView guideView(MarketPaintingGuide guide) {
        return new MarketPaintingGuideView(
                guide.id(), guide.catalogItemId(), guide.version(), guide.knowledgeStatus().id(),
                guide.sources().stream().map(source -> new PaintableProductView.SourceView(
                        source.kind(), source.label(), source.url() == null ? "" : source.url().toString())).toList(),
                guide.slots().stream().map(WorkshopQueryService::guideSlot).toList(),
                guide.preparation().stream().map(WorkshopQueryService::step).toList(),
                guide.painting().stream().map(WorkshopQueryService::step).toList());
    }

    private static MarketPaintingGuideView.SlotView guideSlot(MarketPaintingGuide.Slot slot) {
        return new MarketPaintingGuideView.SlotView(
                slot.id(), slot.role(), text(slot.marketPaintId()), slot.pendingImport(),
                new MarketPaintingGuideView.RequestedPaintView(
                        text(slot.requestedPaint().brand()), text(slot.requestedPaint().name()),
                        text(slot.requestedPaint().colorHex())));
    }

    private static PaintableProductView.GuideStepView step(MarketPaintingGuide.Step step) {
        return new PaintableProductView.GuideStepView(step.title(), step.detail());
    }

    private static WorkshopRecipeView recipeView(WorkshopRecipeState recipe) {
        return new WorkshopRecipeView(
                recipe.id(), recipe.catalogItemId(), defaultText(recipe.basedOnGuideId(), ""),
                defaultText(recipe.supersedesRecipeId(), ""), recipe.displayName(), recipe.version(),
                recipe.status().id(), recipe.solutions(), recipe.updatedAt());
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String defaultText(String value, String fallback) { return present(value) ? value : fallback; }
    private static void require(String value, String field) {
        if (!present(value)) throw new DomainException("invalid_input", field + " is required.");
    }
    private static DomainException notFound(String message) { return new DomainException("not_found", message); }
}
