package com.minipaintdex.application;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.query.SortOrder;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.PaintFacetValue;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.application.view.WorkshopPaintView;
import com.minipaintdex.domain.shared.DomainException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Workshop projection that enriches market references through the market query interface only. */
final class WorkshopPaintQueryService {
    private final MarketCatalogUseCases market;
    private final SnapshotRepository snapshots;

    WorkshopPaintQueryService(MarketCatalogUseCases market, SnapshotRepository snapshots) {
        this.market = Objects.requireNonNull(market);
        this.snapshots = Objects.requireNonNull(snapshots);
    }

    PageResult<WorkshopPaintView> page(
            SearchMarketPaintsQuery filters,
            boolean manufacturerSheetOnly,
            boolean realResultOnly,
            PageQuery page) {
        var quantities = quantities();
        var filtered = market.searchMarketPaints(filters).stream()
                .filter(paint -> quantities.getOrDefault(paint.id(), 0) > 0)
                .filter(paint -> !manufacturerSheetOnly || present(paint.manufacturerUrl()))
                .filter(paint -> !realResultOnly || present(paint.resultImage()) || present(paint.resultReferenceUrl()))
                .map(paint -> new WorkshopPaintView(paint, quantities.get(paint.id())))
                .sorted(comparator(page.sort()))
                .toList();
        var from = Math.min(page.offset(), filtered.size());
        var to = Math.min(from + page.size(), filtered.size());
        return new PageResult<>(filtered.subList(from, to), page.page(), page.size(), filtered.size());
    }

    PaintFacetsView facets() {
        var quantities = quantities();
        var paints = market.searchMarketPaints(SearchMarketPaintsQuery.empty()).stream()
                .filter(paint -> quantities.getOrDefault(paint.id(), 0) > 0)
                .toList();
        return new PaintFacetsView(
                paints.size(),
                facet(paints, paint -> List.of(paint.paintType())),
                facet(paints, paint -> List.of(paint.colorFamily())),
                facet(paints, paint -> List.of(paint.brand())),
                facet(paints, paint -> List.of(paint.manufacturer())),
                facet(paints, paint -> List.of(paint.range())),
                facet(paints, paint -> List.of(paint.finish())),
                facet(paints, paint -> List.of(paint.medium())),
                facet(paints, paint -> List.of(paint.opacity())),
                facet(paints, paint -> List.of(paint.lifecycleStatus())),
                facet(paints, paint -> paint.volumeMl() > 0 ? List.of(paint.volumeMl() + " ml") : List.of()),
                facet(paints, MarketPaintView::tags));
    }

    private Map<String, Integer> quantities() {
        return snapshots.load().paintInventory().stream()
                .map(WorkshopPaintQueryService::documentMap)
                .collect(Collectors.toMap(
                        entry -> text(entry.get("paint_id")),
                        entry -> number(entry.get("quantity")),
                        Integer::sum,
                        LinkedHashMap::new));
    }

    private static Comparator<WorkshopPaintView> comparator(List<SortOrder> orders) {
        var effective = orders.isEmpty() ? List.of(new SortOrder("name", SortOrder.Direction.ASCENDING)) : orders;
        Comparator<WorkshopPaintView> comparator = null;
        for (var order : effective) {
            if (!java.util.Set.of("name", "brand", "range", "reference", "paintType", "colorFamily")
                    .contains(order.property())) {
                throw new DomainException("invalid_input", "Unsupported paint sort property: " + order.property());
            }
            Comparator<WorkshopPaintView> next = Comparator.comparing(
                    value -> sortableValue(value.marketPaint(), order.property()), String.CASE_INSENSITIVE_ORDER);
            if (order.direction() == SortOrder.Direction.DESCENDING) next = next.reversed();
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        return comparator.thenComparing(value -> value.marketPaint().id(), String.CASE_INSENSITIVE_ORDER);
    }

    private static List<PaintFacetValue> facet(
            List<MarketPaintView> paints,
            Function<MarketPaintView, List<String>> values) {
        var counts = new java.util.TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
        paints.forEach(paint -> values.apply(paint).stream().filter(WorkshopPaintQueryService::present)
                .forEach(value -> counts.merge(value, 1, Integer::sum)));
        return counts.entrySet().stream().map(entry -> new PaintFacetValue(entry.getKey(), entry.getValue())).toList();
    }

    private static String sortableValue(MarketPaintView value, String property) {
        return switch (property) {
            case "name" -> value.name();
            case "brand" -> value.brand();
            case "range" -> value.range();
            case "reference" -> value.reference();
            case "paintType" -> value.paintType();
            case "colorFamily" -> value.colorFamily();
            default -> throw new IllegalArgumentException("Unsupported paint sort property: " + property);
        };
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
                    .map(WorkshopPaintQueryService::documentValue).toList();
            case StructuredDocument.ObjectValue object -> documentMap(object.value());
        };
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value)); } catch (NumberFormatException ignored) { return 0; }
    }
}
