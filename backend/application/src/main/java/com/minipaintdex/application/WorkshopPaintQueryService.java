package com.minipaintdex.application;

import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.query.SortOrder;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.application.view.WorkshopPaintView;
import com.minipaintdex.application.validation.StructuredDocuments;
import com.minipaintdex.domain.shared.DomainException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        var filtered = filtered(filters, manufacturerSheetOnly, realResultOnly, quantities)
                .map(paint -> new WorkshopPaintView(paint, quantities.get(paint.id())))
                .sorted(comparator(page.sort()))
                .toList();
        var from = Math.min(page.offset(), filtered.size());
        var to = Math.min(from + page.size(), filtered.size());
        return new PageResult<>(filtered.subList(from, to), page.page(), page.size(), filtered.size());
    }

    PaintFacetsView facets(
            SearchMarketPaintsQuery filters, boolean manufacturerSheetOnly, boolean realResultOnly) {
        var quantities = quantities();
        var paints = filtered(filters, manufacturerSheetOnly, realResultOnly, quantities).toList();
        return MarketPaintQueryService.facets(paints);
    }

    private java.util.stream.Stream<MarketPaintView> filtered(
            SearchMarketPaintsQuery filters,
            boolean manufacturerSheetOnly,
            boolean realResultOnly,
            Map<String, Integer> quantities) {
        return market.searchMarketPaints(filters).stream()
                .filter(paint -> quantities.getOrDefault(paint.id(), 0) > 0)
                .filter(paint -> !manufacturerSheetOnly || present(paint.manufacturerUrl()))
                .filter(paint -> !realResultOnly || present(paint.resultImage()));
    }

    private Map<String, Integer> quantities() {
        return snapshots.load().paintInventory().stream()
                .map(StructuredDocuments::toMap)
                .collect(Collectors.toMap(
                        entry -> StructuredDocuments.text(entry.get("paint_id")),
                        entry -> StructuredDocuments.integer(entry.get("quantity"), "paint_inventory.quantity"),
                        (left, right) -> { throw new DomainException(
                                "invalid_input", "Duplicate workshop paint inventory entry."); },
                        LinkedHashMap::new));
    }

    private static Comparator<WorkshopPaintView> comparator(List<SortOrder> orders) {
        var effective = orders.isEmpty() ? List.of(new SortOrder("name", SortOrder.Direction.ASCENDING)) : orders;
        Comparator<WorkshopPaintView> comparator = null;
        for (var order : effective) {
            if (!java.util.Set.of("name", "brand", "range", "reference", "role", "colorFamily", "verifiedAt")
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

    private static String sortableValue(MarketPaintView value, String property) {
        return switch (property) {
            case "name" -> value.name();
            case "brand" -> value.brand();
            case "range" -> value.range();
            case "reference" -> value.reference();
            case "role" -> value.profile().roles().getFirst();
            case "colorFamily" -> value.colorFamily();
            case "verifiedAt" -> value.manufacturerVerifiedAt();
            default -> throw new IllegalArgumentException("Unsupported paint sort property: " + property);
        };
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
