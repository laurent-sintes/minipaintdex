package com.minipaintdex.application;

import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.view.MarketPaintView;
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
final class MarketProductQueryService {
    private final MarketCatalogReader catalogs;
    private final MarketPaintQueryService paints;

    MarketProductQueryService(MarketCatalogReader catalogs, MarketPaintQueryService paints) {
        this.catalogs = Objects.requireNonNull(catalogs);
        this.paints = Objects.requireNonNull(paints);
    }

    List<PaintableProductSummaryView> summaries() {
        return catalogs.load().paintableProducts().stream()
                .map(product -> new PaintableProductSummaryView(
                        product.id(), product.name(), product.line(), product.productType(), product.scope(),
                        product.catalogItems().size(), product.expectedPaintableCount()))
                .sorted(Comparator.comparing(PaintableProductSummaryView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    PaintableProductView product(String productId) {
        require(productId, "productId");
        return products(catalogs.load()).stream().filter(product -> productId.equals(product.id())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Paintable product not found: " + productId));
    }

    List<MarketPaintingGuideView> guides(String catalogItemId) {
        return catalogs.load().paintingGuides().stream()
                .filter(guide -> !present(catalogItemId) || catalogItemId.equals(guide.catalogItemId()))
                .map(MarketProductQueryService::guide)
                .sorted(Comparator.comparing(MarketPaintingGuideView::id)).toList();
    }

    private List<PaintableProductView> products(MarketCatalogSnapshot snapshot) {
        var guides = snapshot.paintingGuides().stream().collect(Collectors.toMap(
                MarketPaintingGuide::catalogItemId, Function.identity(),
                (left, right) -> left.version() >= right.version() ? left : right));
        var paintsById = paints.views(snapshot).stream().collect(Collectors.toMap(MarketPaintView::id, Function.identity()));
        return snapshot.paintableProducts().stream().map(product -> new PaintableProductView(
                product.schemaVersion(), product.id(), product.name(), product.line(), product.productType(),
                product.scope(), product.expectedPaintableCount(),
                new PaintableProductView.EditionView(product.edition().note(), product.edition().url()),
                product.sources().stream().map(MarketProductQueryService::source).toList(),
                product.catalogItems().stream().map(item -> item(product, item, guides.get(item.id()), paintsById)).toList()))
                .sorted(Comparator.comparing(PaintableProductView::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static PaintableProductView.CatalogItemView item(
            PaintableProduct product,
            PaintableProduct.CatalogItem item,
            MarketPaintingGuide guide,
            Map<String, MarketPaintView> paintsById) {
        var marketGuide = guide == null ? null : new PaintableProductView.MarketGuideView(
                guide.id(), guide.version(), guide.knowledgeStatus().id(),
                guide.sources().stream().map(MarketProductQueryService::source).toList());
        return new PaintableProductView.CatalogItemView(
                item.id(), product.id(), item.name(), item.kind(), item.quantity(), item.description(),
                item.assemblyRequired(), item.referenceImages().stream().map(MarketProductQueryService::image).toList(),
                guide == null ? List.of() : guide.slots().stream()
                        .map(slot -> guidePaint(slot, paintsById)).toList(),
                guide == null ? List.of() : guide.preparation().stream().map(MarketProductQueryService::step).toList(),
                guide == null ? List.of() : guide.painting().stream().map(MarketProductQueryService::step).toList(),
                marketGuide, item.sources().stream().map(MarketProductQueryService::source).toList());
    }

    private static PaintableProductView.GuidePaintView guidePaint(
            MarketPaintingGuide.Slot slot,
            Map<String, MarketPaintView> paintsById) {
        var paint = paintsById.get(slot.marketPaintId());
        var requested = slot.requestedPaint();
        return new PaintableProductView.GuidePaintView(
                slot.id(), paint == null ? "" : paint.id(),
                paint == null ? string(requested.brand()) : paint.brand(),
                paint == null ? string(requested.name()) : paint.name(), slot.role(),
                paint == null ? string(requested.colorHex()) : paint.colorHex(), slot.pendingImport());
    }

    private static MarketPaintingGuideView guide(MarketPaintingGuide guide) {
        return new MarketPaintingGuideView(
                guide.id(), guide.catalogItemId(), guide.version(), guide.knowledgeStatus().id(),
                guide.sources().stream().map(MarketProductQueryService::source).toList(),
                guide.slots().stream().map(slot -> new MarketPaintingGuideView.SlotView(
                        slot.id(), slot.role(), string(slot.marketPaintId()), slot.pendingImport(),
                        new MarketPaintingGuideView.RequestedPaintView(
                                string(slot.requestedPaint().brand()), string(slot.requestedPaint().name()),
                                string(slot.requestedPaint().colorHex())))).toList(),
                guide.preparation().stream().map(MarketProductQueryService::step).toList(),
                guide.painting().stream().map(MarketProductQueryService::step).toList());
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
