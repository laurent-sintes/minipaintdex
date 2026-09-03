package com.minipaintdex.application;

import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.query.SearchPaintPotsQuery;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.view.PaintPotView;
import com.minipaintdex.domain.shared.DomainException;
import com.minipaintdex.domain.workshop.PaintPot;
import com.minipaintdex.domain.workshop.PaintPotPossession;
import com.minipaintdex.domain.workshop.PaintPotProjector;
import java.util.Comparator;

final class PaintPotQueryService {
    private final SnapshotRepository snapshots;
    private final MarketCatalogUseCases market;
    PaintPotQueryService(SnapshotRepository snapshots, MarketCatalogUseCases market) {
        this.snapshots = snapshots; this.market = market;
    }
    PaintPotView get(String id) {
        var pot = PaintPotProjector.project(snapshots.load().events()).stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Paint pot not found: " + id));
        return view(pot);
    }
    PageResult<PaintPotView> search(SearchPaintPotsQuery query) {
        if (!query.page().sort().isEmpty()) throw new DomainException("invalid_input", "Paint pots are ordered by stable identity.");
        var pots = PaintPotProjector.project(snapshots.load().events()).stream()
                .filter(pot -> query.paintProductId() == null || query.paintProductId().isBlank() || pot.paintProductId().equals(query.paintProductId()))
                .filter(pot -> query.includeRemoved() || pot.possession() == PaintPotPossession.OWNED)
                .sorted(Comparator.comparing(PaintPot::id)).toList();
        var from = Math.min(query.page().offset(), pots.size());
        var to = Math.min(from + query.page().size(), pots.size());
        var selected = pots.subList(from, to);
        var productIds = selected.stream().map(PaintPot::paintProductId).collect(java.util.stream.Collectors.toSet());
        if (selected.isEmpty()) return new PageResult<>(java.util.List.of(), query.page().page(), query.page().size(), pots.size());
        // One Market generation per page: do not rebuild the full catalog separately for every pot.
        try (var products = market.streamPaintProducts(com.minipaintdex.application.query.SearchPaintProductsQuery.empty())) {
            var byId = products.filter(product -> productIds.contains(product.id()))
                    .collect(java.util.stream.Collectors.toMap(com.minipaintdex.application.view.PaintProductView::id, java.util.function.Function.identity()));
            var views = selected.stream().map(pot -> {
                var product = byId.get(pot.paintProductId());
                if (product == null) throw new DomainException("not_found", "Paint product not found: " + pot.paintProductId());
                return PaintPotView.from(pot, product);
            }).toList();
            return new PageResult<>(views, query.page().page(), query.page().size(), pots.size());
        }
    }
    private PaintPotView view(PaintPot pot) {
        return PaintPotView.from(pot, market.getPaintProduct(pot.paintProductId()));
    }
}
