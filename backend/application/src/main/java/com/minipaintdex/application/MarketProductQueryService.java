package com.minipaintdex.application;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.MarketPaintingGuideView;
import com.minipaintdex.application.view.PaintableProductSummaryView;
import com.minipaintdex.application.view.PaintableProductView;
import com.minipaintdex.domain.market.product.PaintableProduct;
import com.minipaintdex.domain.shared.DomainException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only market product and painting-guide projections. */
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
        var snapshot = catalogs.load();
        return products(snapshot).stream().filter(product -> productId.equals(product.id())).findFirst()
                .orElseThrow(() -> new DomainException(
                        "not_found", "Paintable product not found: " + productId));
    }

    List<MarketPaintingGuideView> guides(String catalogItemId) {
        return documentMaps(catalogs.load().paintingGuides()).stream()
                .filter(guide -> !present(catalogItemId)
                        || catalogItemId.equals(text(guide.get("catalog_item_id"))))
                .map(this::guide)
                .sorted(Comparator.comparing(MarketPaintingGuideView::id))
                .toList();
    }

    private List<PaintableProductView> products(MarketCatalogSnapshot snapshot) {
        var guides = documentMaps(snapshot.paintingGuides()).stream().collect(Collectors.toMap(
                entry -> text(entry.get("catalog_item_id")), Function.identity(),
                (left, right) -> number(left.get("version")) >= number(right.get("version")) ? left : right));
        var paintsById = paints.views(snapshot).stream()
                .collect(Collectors.toMap(MarketPaintView::id, Function.identity()));
        return snapshot.paintableProducts().stream().map(product -> {
            var items = product.catalogItems().stream().map(item -> {
                var guide = guides.getOrDefault(item.id(), Map.of());
                var marketGuide = guide.isEmpty() ? null : new PaintableProductView.MarketGuideView(
                        text(guide.get("id")), number(guide.get("version")),
                        text(guide.get("knowledge_status")),
                        listOfMaps(guide.get("sources")).stream().map(this::source).toList());
                return new PaintableProductView.CatalogItemView(
                        item.id(), product.id(), item.name(), item.kind(), item.quantity(), item.description(),
                        item.assemblyRequired(), item.referenceImages().stream().map(this::image).toList(),
                        listOfMaps(guide.get("slots")).stream()
                                .map(slot -> guidePaint(slot, paintsById)).toList(),
                        listOfMaps(guide.get("preparation")).stream().map(this::step).toList(),
                        listOfMaps(guide.get("painting")).stream().map(this::step).toList(),
                        marketGuide, item.sources().stream().map(this::source).toList());
            }).toList();
            return new PaintableProductView(
                    product.schemaVersion(), product.id(), product.name(), product.line(), product.productType(),
                    product.scope(), product.expectedPaintableCount(),
                    new PaintableProductView.EditionView(product.edition().note(), product.edition().url()),
                    product.sources().stream().map(this::source).toList(), items);
        }).sorted(Comparator.comparing(PaintableProductView::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private MarketPaintingGuideView guide(Map<String, Object> guide) {
        return new MarketPaintingGuideView(
                text(guide.get("id")), text(guide.get("catalog_item_id")), number(guide.get("version")),
                text(guide.get("knowledge_status")),
                listOfMaps(guide.get("sources")).stream().map(this::source).toList(),
                listOfMaps(guide.get("slots")).stream().map(this::slot).toList(),
                listOfMaps(guide.get("preparation")).stream().map(this::step).toList(),
                listOfMaps(guide.get("painting")).stream().map(this::step).toList());
    }

    private PaintableProductView.GuidePaintView guidePaint(
            Map<String, Object> slot, Map<String, MarketPaintView> paintsById) {
        var paint = paintsById.get(text(slot.get("market_paint_id")));
        var requested = map(slot.get("requested_paint"));
        return new PaintableProductView.GuidePaintView(
                text(slot.get("id")), paint == null ? "" : paint.id(),
                paint == null ? text(requested.get("brand")) : paint.brand(),
                paint == null ? text(requested.get("name")) : paint.name(),
                text(slot.get("role")),
                paint == null ? text(requested.get("color_hex")) : paint.colorHex(),
                Boolean.TRUE.equals(slot.get("pending_import")));
    }

    private MarketPaintingGuideView.SlotView slot(Map<String, Object> slot) {
        var requested = map(slot.get("requested_paint"));
        return new MarketPaintingGuideView.SlotView(
                text(slot.get("id")), text(slot.get("role")), text(slot.get("market_paint_id")),
                Boolean.TRUE.equals(slot.get("pending_import")),
                new MarketPaintingGuideView.RequestedPaintView(
                        text(requested.get("brand")), text(requested.get("name")),
                        text(requested.get("color_hex"))));
    }

    private PaintableProductView.SourceView source(Map<String, Object> source) {
        return new PaintableProductView.SourceView(
                text(source.get("kind")), text(source.get("label")), text(source.get("url")));
    }

    private PaintableProductView.SourceView source(PaintableProduct.Source source) {
        return new PaintableProductView.SourceView(source.kind(), source.label(), source.url());
    }

    private PaintableProductView.ReferenceImageView image(PaintableProduct.ReferenceImage image) {
        return new PaintableProductView.ReferenceImageView(
                image.url(), image.pageUrl(), image.credit(), image.license());
    }

    private PaintableProductView.GuideStepView step(Map<String, Object> step) {
        return new PaintableProductView.GuideStepView(text(step.get("title")), text(step.get("detail")));
    }

    private static List<Map<String, Object>> documentMaps(List<StructuredDocument> documents) {
        return documents.stream().map(MarketProductQueryService::documentMap).toList();
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
                    .map(MarketProductQueryService::documentValue).toList();
            case StructuredDocument.ObjectValue object -> documentMap(object.value());
        };
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance)
                .map(MarketProductQueryService::map).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private static void require(String value, String field) {
        if (!present(value)) throw new DomainException("invalid_input", field + " is required.");
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value)); } catch (NumberFormatException ignored) { return 0; }
    }
}
