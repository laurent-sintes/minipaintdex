package com.minipaintdex.application;

import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.query.GetPaintUsageGuideQuery;
import com.minipaintdex.application.query.SearchPaintUsageGuidesQuery;
import com.minipaintdex.application.result.PaintUsageGuideResult;
import com.minipaintdex.application.result.PaintUsageGuidesResult;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.view.PaintUsageGuideView;
import com.minipaintdex.domain.market.paint.PaintUsageGuide;
import com.minipaintdex.domain.shared.DomainException;
import java.util.Comparator;
import java.util.Set;

final class PaintUsageGuideQueryService {
    private final MarketCatalogReader catalogs;
    PaintUsageGuideQueryService(MarketCatalogReader catalogs) { this.catalogs = catalogs; }
    PaintUsageGuideResult get(GetPaintUsageGuideQuery query) {
        var guide = catalogs.load().paintUsageGuides().stream().filter(g -> g.id().equals(query.paintUsageGuideId()))
                .findFirst().orElseThrow(() -> new DomainException("not_found", "Paint usage guide not found"));
        return new PaintUsageGuideResult(PaintUsageGuideView.from(guide, query.language()), query.correlationId());
    }
    PaintUsageGuidesResult search(SearchPaintUsageGuidesQuery query) {
        var snapshot = catalogs.load();
        Set<String> ids = query.paintProductId() == null ? null : Set.copyOf(snapshot.paints().stream()
                .filter(p -> p.id().equals(query.paintProductId())).findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Paint product not found")).usageGuideIds());
        Comparator<PaintUsageGuide> order = Comparator.comparing(PaintUsageGuide::id);
        for (var sort : query.page().sort().reversed()) {
            Comparator<PaintUsageGuide> field = switch (sort.property()) {
                case "id" -> Comparator.comparing(PaintUsageGuide::id);
                case "title" -> Comparator.comparing(PaintUsageGuide::title, String.CASE_INSENSITIVE_ORDER);
                default -> throw new DomainException("invalid_input", "Guide sort must be id or title");
            };
            if (sort.direction() == com.minipaintdex.application.query.SortOrder.Direction.DESCENDING) field = field.reversed();
            order = field.thenComparing(order);
        }
        var rows = snapshot.paintUsageGuides().stream()
                .filter(g -> ids == null || ids.contains(g.id()))
                .filter(g -> query.brand() == null || query.brand().equals(g.brand()))
                .filter(g -> query.range() == null || g.ranges().contains(query.range()))
                .sorted(order).toList();
        var start = Math.min(query.page().offset(), rows.size());
        var views = rows.subList(start, Math.min(start + query.page().size(), rows.size())).stream()
                .map(g -> PaintUsageGuideView.from(g, query.language())).toList();
        return new PaintUsageGuidesResult(new PageResult<>(views, query.page().page(), query.page().size(), rows.size()), query.correlationId());
    }
}
