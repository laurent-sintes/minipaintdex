package com.minipaintdex.application;

import com.minipaintdex.application.query.PaintSearchQuery;
import com.minipaintdex.application.query.PaintSearchPolicy;
import com.minipaintdex.application.result.PaintSearchResult;
import com.minipaintdex.application.view.PaintProductSuggestion;

import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.query.SearchPaintProductsQuery;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.view.PaintProductView;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.application.view.WorkshopPaintStockView;
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

    PaintSearchResult<WorkshopPaintStockView> search(
            PaintSearchQuery query,
            PaintSearchPolicy policy) {
        var limit = query.limit(policy);
        if (!query.includesResults() && query.filters().query().isBlank())
            return new PaintSearchResult<>(null, List.of(), query.correlationId());
        var pots = com.minipaintdex.domain.workshop.PaintPotProjector.project(snapshots.load().events());
        var selection = filtered(query.filters(), query.manufacturerSheetOnly(), query.realResultOnly(), quantities(pots));
        if (!query.includesResults()) return new PaintSearchResult<>(
                null, selection.limit(limit).map(PaintProductSuggestion::from).toList(), query.correlationId());
        var ranked = selection.toList();
        var suggestions = !query.includesSuggestions() ? null : query.filters().query().isBlank()
                ? List.<PaintProductSuggestion>of()
                : ranked.stream().limit(limit).map(PaintProductSuggestion::from).toList();
        var ordered = PaintSearch.order(ranked, query.page().sort());
        var from = Math.min(query.page().offset(), ordered.size());
        var to = Math.min(from + query.page().size(), ordered.size());
        var content = ordered.subList(from, to).stream().map(paint -> {
            var owned = pots.stream().filter(pot -> pot.paintProductId().equals(paint.id())
                    && pot.possession() == com.minipaintdex.domain.workshop.PaintPotPossession.OWNED).toList();
            var available = (int) owned.stream().filter(com.minipaintdex.domain.workshop.PaintPot::available).count();
            var photo = owned.stream().flatMap(pot -> pot.photos().stream())
                    .max(Comparator.comparing(com.minipaintdex.domain.workshop.PaintPotEvent.PaintPotPhotoAdded::occurredAt))
                    .map(photoEntry -> photoEntry.cutout() == null ? photoEntry.url() : photoEntry.cutout().url()).orElse(null);
            return new WorkshopPaintStockView(paint, owned.size(), available, photo);
        }).toList();
        return new PaintSearchResult<>(
                new PageResult<>(content, query.page().page(), query.page().size(), ordered.size()), suggestions, query.correlationId());
    }

    PaintFacetsView facets(
            SearchPaintProductsQuery filters, boolean manufacturerSheetOnly, boolean realResultOnly) {
        var quantities = quantities(com.minipaintdex.domain.workshop.PaintPotProjector.project(snapshots.load().events()));
        var paints = filtered(SearchPaintProductsQuery.empty(), manufacturerSheetOnly, realResultOnly, quantities).toList();
        var matches = filters.query().isBlank() ? paints.stream().map(PaintProductView::id).collect(Collectors.toSet())
                : market.searchPaintProducts(SearchPaintProductsQuery.fromSelections(filters.query(),
                    null, null, null, null, null, null, null, null, null, null, null, null))
                    .stream().map(PaintProductView::id).collect(Collectors.toSet());
        return PaintSearch.facets(paints, filters, matches);
    }

    private java.util.stream.Stream<PaintProductView> filtered(
            SearchPaintProductsQuery filters,
            boolean manufacturerSheetOnly,
            boolean realResultOnly,
            Map<String, Integer> quantities) {
        return market.searchPaintProducts(filters).stream()
                .filter(paint -> quantities.getOrDefault(paint.id(), 0) > 0)
                .filter(paint -> !manufacturerSheetOnly || present(paint.manufacturerUrl()))
                .filter(paint -> !realResultOnly || present(paint.resultImage()));
    }

    private Map<String, Integer> quantities(List<com.minipaintdex.domain.workshop.PaintPot> pots) {
        var quantities = new LinkedHashMap<String, Integer>();
        pots.stream().filter(pot -> pot.possession() == com.minipaintdex.domain.workshop.PaintPotPossession.OWNED)
                .forEach(pot -> quantities.merge(pot.paintProductId(), 1, Integer::sum));
        return quantities;
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
