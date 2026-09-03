package com.minipaintdex.application;

import com.minipaintdex.application.query.PaintSearchQuery;
import com.minipaintdex.application.query.PaintSearchPolicy;
import com.minipaintdex.application.result.PaintSearchResult;
import com.minipaintdex.application.view.PaintProductSuggestion;

import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.query.SearchPaintProductsQuery;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.view.PaintProductView;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.application.view.PaintCatalogQualityView;
import com.minipaintdex.domain.market.paint.PaintProduct;
import com.minipaintdex.domain.shared.DomainException;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Read-only market-paint application service over the canonical paint profile. */
final class PaintProductQueryService {
    private final MarketCatalogReader catalogs;
    private final com.minipaintdex.application.port.PaintProductSearchIndex index;

    PaintProductQueryService(MarketCatalogReader catalogs, com.minipaintdex.application.port.PaintProductSearchIndex index) {
        this.catalogs = Objects.requireNonNull(catalogs);
        this.index = Objects.requireNonNull(index);
    }


    PaintProductView get(String id) {
        var snapshot = catalogs.load();
        return snapshot.paints().stream().filter(paint -> paint.id().equals(id)).findFirst()
                .map(paint -> view(paint, snapshot))
                .orElseThrow(() -> new DomainException("not_found", "Paint product not found: " + id));
    }

    List<PaintProductView> search(SearchPaintProductsQuery filters) {
        return stream(filters).toList();
    }

    Stream<PaintProductView> stream(SearchPaintProductsQuery filters) {
        var snapshot = catalogs.load();
        var byId = snapshot.paints().stream().collect(java.util.stream.Collectors.toMap(PaintProduct::id, p -> p));
        return index.rank(snapshot.paints(), filters.query()).stream().map(byId::get)
                .map(paint -> view(paint, snapshot)).filter(paint -> PaintSearch.matches(paint, filters));
    }

    PaintSearchResult<PaintProductView> search(
            PaintSearchQuery query,
            PaintSearchPolicy policy) {
        var limit = query.limit(policy);
        if (!query.includesResults() && query.filters().query().isBlank())
            return new PaintSearchResult<>(null, List.of(), query.correlationId());
        // One immutable Market generation and one ranked selection for every requested part.
        var selection = filtered(query.filters(), query.manufacturerSheetOnly(), query.realResultOnly());
        if (!query.includesResults()) return new PaintSearchResult<>(
                null, selection.limit(limit).map(PaintProductSuggestion::from).toList(), query.correlationId());
        var ranked = selection.toList();
        var suggestions = !query.includesSuggestions() ? null : query.filters().query().isBlank()
                ? List.<PaintProductSuggestion>of()
                : ranked.stream().limit(limit).map(PaintProductSuggestion::from).toList();
        var ordered = PaintSearch.order(ranked, query.page().sort());
        var from = Math.min(query.page().offset(), ordered.size());
        var to = Math.min(from + query.page().size(), ordered.size());
        return new PaintSearchResult<>(
                new PageResult<>(ordered.subList(from, to), query.page().page(), query.page().size(), ordered.size()),
                suggestions, query.correlationId());
    }

    PaintFacetsView facets(
            SearchPaintProductsQuery filters, boolean manufacturerSheetOnly, boolean realResultOnly) {
        var snapshot = catalogs.load();
        var matching = java.util.Set.copyOf(index.rank(snapshot.paints(), filters.query()));
        var paints = views(snapshot).stream()
                .filter(paint -> !manufacturerSheetOnly || present(paint.manufacturerUrl()))
                .filter(paint -> !realResultOnly || present(paint.resultImage())).toList();
        return PaintSearch.facets(paints, filters, matching);
    }

    PaintCatalogQualityView quality() {
        var snapshot = catalogs.load();
        var paints = snapshot.paints();
        var qualityCounts = new java.util.TreeMap<String, Integer>();
        var limitationCounts = new java.util.TreeMap<String, Integer>();
        paints.forEach(paint -> qualityCounts.merge(paint.manufacturerImage().imageQuality().id(), 1, Integer::sum));
        paints.stream().filter(paint -> paint.manufacturerImage().qualityLimitation() != null)
                .forEach(paint -> limitationCounts.merge(
                        paint.brand() + "\u0000" + paint.manufacturerImage().qualityLimitation().code().id(),
                        1, Integer::sum));
        return new PaintCatalogQualityView(
                paints.size(),
                (int) paints.stream().filter(paint -> !"auxiliary".equalsIgnoreCase(string(paint.color().family()))
                        && !present(paint.color().hex())).count(),
                (int) paints.stream().filter(paint -> !present(paint.color().family())).count(),
                (int) paints.stream().filter(paint -> "unknown".equals(paint.profile().finish().id())).count(),
                (int) paints.stream().filter(paint -> "unknown".equals(paint.profile().coverage().id())).count(),
                (int) paints.stream().filter(paint -> paint.profile().requiresUsageInstructions()
                        && (paint.usageInstructions().reviewRequired() || snapshot.paintUsageGuides().stream()
                            .anyMatch(g -> paint.usageGuideIds().contains(g.id()) && g.reviewRequired()))).count(),
                (int) paints.stream().filter(paint -> paint.manufacturerImage().imageQuality().rank() <= 4
                        && !present(paint.manufacturerImage().license())).count(),
                (int) paints.stream().filter(paint -> present(image(paint.resultImage()))).count(),
                qualityCounts.entrySet().stream()
                        .map(entry -> new PaintCatalogQualityView.ImageQualityCount(entry.getKey(), entry.getValue()))
                        .toList(),
                limitationCounts.entrySet().stream()
                        .map(entry -> {
                            var separator = entry.getKey().indexOf('\u0000');
                            return new PaintCatalogQualityView.ImageLimitationCount(
                                    entry.getKey().substring(0, separator),
                                    entry.getKey().substring(separator + 1), entry.getValue());
                        })
                        .toList());
    }

    private Stream<PaintProductView> filtered(
            SearchPaintProductsQuery filters, boolean manufacturerSheetOnly, boolean realResultOnly) {
        return stream(filters)
                .filter(paint -> !manufacturerSheetOnly || present(paint.manufacturerUrl()))
                .filter(paint -> !realResultOnly || present(paint.resultImage()));
    }

    static List<PaintProductView> views(MarketCatalogSnapshot snapshot) {
        return snapshot.paints().stream().map(paint -> view(paint, snapshot))
                .sorted(Comparator.comparing(PaintProductView::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static PaintProductView view(PaintProduct paint, MarketCatalogSnapshot snapshot) {
        var usage = paint.usageInstructions();
        var profile = paint.profile();
        var technical = profile.requiresUsageInstructions();
        var verifiedAt = paint.verifiedAt() == null ? "" : paint.verifiedAt().toString();
        return new PaintProductView(
                paint.id(), paint.brand(), paint.manufacturer(), paint.brandAliases(), paint.range(),
                new PaintProductView.Profile(
                        profile.roleIds(), profile.applicationMethodIds(), profile.applicationSystem().id(),
                        profile.coverage().id(), profile.finish().id(), profile.effectIds(),
                        profile.undercoat().tone().id(), profile.undercoat().preHighlightedSurfaceRecommended(),
                        profile.medium().id()),
                string(paint.reference()), paint.name(), string(paint.color().hex()), paint.lifecycle().id(),
                paint.dataStatus(), String.join(" · ", paint.warnings()), paint.tags(), string(paint.notes()),
                verifiedAt.isBlank() ? "" : verifiedAt + "T00:00:00.000Z",
                verifiedAt.isBlank() ? "" : verifiedAt + "T00:00:00.000Z",
                uri(paint.manufacturerPage()), image(paint.manufacturerImage()), uri(paint.manufacturerImage().sourceUrl()),
                string(paint.manufacturerImage().credit()), paint.manufacturerImage().imageQuality().id(),
                paint.manufacturerImage().imageQuality().rank(),
                paint.manufacturerImage().qualityVerifiedAt() == null ? "" : paint.manufacturerImage().qualityVerifiedAt().toString(),
                paint.manufacturerImage().qualityLimitation() == null ? ""
                        : paint.manufacturerImage().qualityLimitation().code().id(),
                paint.manufacturerImage().qualityLimitation() == null ? ""
                        : paint.manufacturerImage().qualityLimitation().detail(),
                paint.manufacturerImage().qualityLimitation() == null ? ""
                        : paint.manufacturerImage().qualityLimitation().observedAt().toString(),
                paint.volumeMl(), string(paint.color().family()),
                string(paint.notes()), paint.recommendedUses(),
                new PaintProductView.UsageInstructions(
                        string(usage.summary()), usage.steps(), usage.tips(),
                        usage.instructionStatus() == null && technical
                                ? "legacy_unverified" : string(usage.instructionStatus()),
                        usage.reviewRequired() || technical && usage.instructionStatus() == null),
                verifiedAt, image(paint.resultImage()), string(paint.resultImage().credit()),
                uri(paint.resultImage().sourceUrl()), string(paint.resultImage().license()),
                uri(paint.resultImage().referenceUrl()), paint.catalogMemberships().stream().map(membership -> {
                    var edition = snapshot.paintCatalogEditions().stream()
                            .filter(e -> e.id().equals(membership.catalogEditionId())).findFirst().orElseThrow();
                    return new PaintProductView.CatalogMembership(edition.id(), edition.title(), edition.editionLabel(),
                            edition.publicationYear(), membership.sourceUrl().toString(), membership.locator());
                }).toList(), paint.usageGuideIds());
    }

    private static String uri(java.net.URI value) { return value == null ? "" : value.toString(); }
    private static String image(PaintProduct.ImageReference value) {
        return present(value.path()) ? value.path() : uri(value.sourceUrl());
    }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
