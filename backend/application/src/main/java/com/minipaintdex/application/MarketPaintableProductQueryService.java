package com.minipaintdex.application;

import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.view.PaintProductView;
import com.minipaintdex.application.view.MarketPaintingGuideView;
import com.minipaintdex.application.view.PaintableProductSummaryView;
import com.minipaintdex.application.view.PaintableProductView;
import com.minipaintdex.domain.market.guide.MarketPaintingGuide;
import com.minipaintdex.domain.market.product.PaintableProduct;
import com.minipaintdex.domain.shared.DomainException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only market product and painting-guide projections over typed aggregate roots. */
final class MarketPaintableProductQueryService {
    private final MarketCatalogReader catalogs;
    private final PaintProductQueryService paints;

    MarketPaintableProductQueryService(MarketCatalogReader catalogs, PaintProductQueryService paints) {
        this.catalogs = Objects.requireNonNull(catalogs);
        this.paints = Objects.requireNonNull(paints);
    }

    List<PaintableProductSummaryView> summaries() {
        return catalogs.load().paintableProducts().stream()
                .map(product -> new PaintableProductSummaryView(
                        product.id(), product.name(), product.line(), product.productType(), product.scope(),
                        product.paintableComponents().size(), product.expectedPaintableCount()))
                .sorted(Comparator.comparing(PaintableProductSummaryView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    PaintableProductView product(String paintableProductId) {
        require(paintableProductId, "paintableProductId");
        return products(catalogs.load()).stream().filter(product -> paintableProductId.equals(product.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Paintable product not found: " + paintableProductId));
    }

    List<MarketPaintingGuideView> guides(String paintableComponentId) {
        return catalogs.load().paintingGuides().stream()
                .filter(guide -> !present(paintableComponentId) || paintableComponentId.equals(guide.paintableComponentId()))
                .map(MarketPaintableProductQueryService::guide)
                .sorted(Comparator.comparing(MarketPaintingGuideView::id)).toList();
    }

    private List<PaintableProductView> products(MarketCatalogSnapshot snapshot) {
        var guides = snapshot.paintingGuides().stream().collect(Collectors.toMap(
                MarketPaintingGuide::paintableComponentId, Function.identity(),
                (left, right) -> left.version() >= right.version() ? left : right));
        var paintsById = paints.views(snapshot).stream().collect(Collectors.toMap(PaintProductView::id, Function.identity()));
        return snapshot.paintableProducts().stream().map(product -> new PaintableProductView(
                product.schemaVersion(), product.id(), product.name(), product.line(), product.productType(),
                product.scope(), product.expectedPaintableCount(),
                new PaintableProductView.EditionView(product.edition().note(), product.edition().url()),
                product.sources().stream().map(MarketPaintableProductQueryService::source).toList(),
                product.paintableComponents().stream().map(item -> item(product, item, guides.get(item.id()), paintsById)).toList()))
                .sorted(Comparator.comparing(PaintableProductView::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static PaintableProductView.PaintableComponentView item(
            PaintableProduct product,
            PaintableProduct.PaintableComponent item,
            MarketPaintingGuide guide,
            Map<String, PaintProductView> paintsById) {
        var marketGuide = guide == null ? null : new PaintableProductView.MarketGuideView(
                guide.id(), guide.version(), guide.knowledgeStatus().id(),
                guide.sources().stream().map(MarketPaintableProductQueryService::source).toList());
        return new PaintableProductView.PaintableComponentView(
                item.id(), product.id(), item.name(), item.kind(), item.quantity(), item.description(),
                item.assemblyRequired(), item.referenceImages().stream().map(MarketPaintableProductQueryService::image).toList(),
                guide == null ? List.of() : guide.slots().stream()
                        .map(slot -> guidePaint(slot, paintsById)).toList(),
                guide == null ? List.of() : guide.preparation().stream().map(MarketPaintableProductQueryService::step).toList(),
                guide == null ? List.of() : guide.painting().stream().map(MarketPaintableProductQueryService::step).toList(),
                marketGuide, item.sources().stream().map(MarketPaintableProductQueryService::source).toList());
    }

    private static PaintableProductView.GuidePaintView guidePaint(
            MarketPaintingGuide.Slot slot,
            Map<String, PaintProductView> paintsById) {
        var paint = paintsById.get(slot.paintProductId());
        var requested = slot.requestedPaint();
        return new PaintableProductView.GuidePaintView(
                slot.id(), paint == null ? "" : paint.id(),
                paint == null ? string(requested.brand()) : paint.brand(),
                paint == null ? string(requested.name()) : paint.name(), slot.role(),
                paint == null ? string(requested.colorHex()) : paint.colorHex(), slot.pendingImport());
    }

    private static MarketPaintingGuideView guide(MarketPaintingGuide guide) {
        return new MarketPaintingGuideView(
                guide.id(), guide.paintableComponentId(), guide.version(), guide.knowledgeStatus().id(),
                guide.sources().stream().map(MarketPaintableProductQueryService::source).toList(),
                guide.slots().stream().map(slot -> new MarketPaintingGuideView.SlotView(
                        slot.id(), slot.role(), string(slot.paintProductId()), slot.pendingImport(),
                        new MarketPaintingGuideView.RequestedPaintView(
                                string(slot.requestedPaint().brand()), string(slot.requestedPaint().name()),
                                string(slot.requestedPaint().colorHex())))).toList(),
                guide.preparation().stream().map(MarketPaintableProductQueryService::step).toList(),
                guide.painting().stream().map(MarketPaintableProductQueryService::step).toList());
    }

    private static PaintableProductView.SourceView source(MarketPaintingGuide.Source source) {
        return new PaintableProductView.SourceView(
                source.kind(), source.label(), source.url() == null ? "" : source.url().toString());
    }

    private static PaintableProductView.SourceView source(PaintableProduct.Source source) {
        return new PaintableProductView.SourceView(source.kind(), source.label(), source.url());
    }

    private static PaintableProductView.ReferenceImageView image(PaintableProduct.ReferenceImage image) {
        return new PaintableProductView.ReferenceImageView(
                image.url(), image.pageUrl(), image.credit(), image.license());
    }

    private static PaintableProductView.GuideStepView step(MarketPaintingGuide.Step step) {
        return new PaintableProductView.GuideStepView(step.title(), step.detail());
    }

    private static void require(String value, String field) {
        if (!present(value)) throw new DomainException("invalid_input", field + " is required.");
    }

    private static String string(String value) { return value == null ? "" : value; }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
