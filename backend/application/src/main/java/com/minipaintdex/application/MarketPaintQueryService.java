package com.minipaintdex.application;

import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.query.SortOrder;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.application.view.PaintCatalogQualityView;
import com.minipaintdex.domain.market.paint.MarketPaint;
import com.minipaintdex.domain.shared.DomainException;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Read-only market-paint application service over the canonical paint profile. */
final class MarketPaintQueryService {
    private final MarketCatalogReader catalogs;

    MarketPaintQueryService(MarketCatalogReader catalogs) {
        this.catalogs = Objects.requireNonNull(catalogs);
    }

    List<MarketPaintView> search(SearchMarketPaintsQuery filters) {
        return stream(filters).sorted(Comparator.comparing(
                MarketPaintView::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    Stream<MarketPaintView> stream(SearchMarketPaintsQuery filters) {
        var snapshot = catalogs.load();
        return snapshot.paints().stream().map(paint -> view(paint, snapshot))
                .filter(paint -> PaintSearch.matches(paint, filters));
    }

    PageResult<MarketPaintView> page(
            SearchMarketPaintsQuery filters,
            boolean manufacturerSheetOnly,
            boolean realResultOnly,
            PageQuery page) {
        var filtered = filtered(filters, manufacturerSheetOnly, realResultOnly)
                .sorted(comparator(page.sort())).toList();
        var from = Math.min(page.offset(), filtered.size());
        var to = Math.min(from + page.size(), filtered.size());
        return new PageResult<>(filtered.subList(from, to), page.page(), page.size(), filtered.size());
    }

    PaintFacetsView facets(
            SearchMarketPaintsQuery filters, boolean manufacturerSheetOnly, boolean realResultOnly) {
        return PaintSearch.facets(filtered(SearchMarketPaintsQuery.empty(), manufacturerSheetOnly, realResultOnly).toList(), filters);
    }

    PaintCatalogQualityView quality() {
        var paints = catalogs.load().paints();
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
                        && paint.usageInstructions().reviewRequired()).count(),
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

    private Stream<MarketPaintView> filtered(
            SearchMarketPaintsQuery filters, boolean manufacturerSheetOnly, boolean realResultOnly) {
        return stream(filters)
                .filter(paint -> !manufacturerSheetOnly || present(paint.manufacturerUrl()))
                .filter(paint -> !realResultOnly || present(paint.resultImage()));
    }

    List<MarketPaintView> views(MarketCatalogSnapshot snapshot) {
        return snapshot.paints().stream().map(paint -> view(paint, snapshot))
                .sorted(Comparator.comparing(MarketPaintView::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static MarketPaintView view(MarketPaint paint, MarketCatalogSnapshot snapshot) {
        var usage = paint.usageInstructions();
        var profile = paint.profile();
        var technical = profile.requiresUsageInstructions();
        var verifiedAt = paint.verifiedAt() == null ? "" : paint.verifiedAt().toString();
        return new MarketPaintView(
                paint.id(), paint.brand(), paint.manufacturer(), paint.brandAliases(), paint.range(),
                new MarketPaintView.Profile(
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
                new MarketPaintView.UsageInstructions(
                        string(usage.summary()), usage.steps(), usage.tips(),
                        usage.instructionStatus() == null && technical
                                ? "legacy_unverified" : string(usage.instructionStatus()),
                        usage.reviewRequired() || technical && usage.instructionStatus() == null),
                verifiedAt, image(paint.resultImage()), string(paint.resultImage().credit()),
                uri(paint.resultImage().sourceUrl()), string(paint.resultImage().license()),
                uri(paint.resultImage().referenceUrl()), paint.catalogMemberships().stream().map(membership -> {
                    var edition = snapshot.paintCatalogEditions().stream()
                            .filter(e -> e.id().equals(membership.catalogEditionId())).findFirst().orElseThrow();
                    return new MarketPaintView.CatalogMembership(edition.id(), edition.title(), edition.editionLabel(),
                            edition.publicationYear(), membership.sourceUrl().toString(), membership.locator());
                }).toList());
    }

    private static Comparator<MarketPaintView> comparator(List<SortOrder> orders) {
        var effective = orders.isEmpty() ? List.of(new SortOrder("name", SortOrder.Direction.ASCENDING)) : orders;
        Comparator<MarketPaintView> comparator = null;
        for (var order : effective) {
            if (!java.util.Set.of("name", "brand", "range", "reference", "role", "colorFamily", "verifiedAt")
                    .contains(order.property())) {
                throw new DomainException("invalid_input", "Unsupported paint sort property: " + order.property());
            }
            Comparator<MarketPaintView> next = Comparator.comparing(
                    value -> sortableValue(value, order.property()), String.CASE_INSENSITIVE_ORDER);
            if (order.direction() == SortOrder.Direction.DESCENDING) next = next.reversed();
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        return comparator.thenComparing(MarketPaintView::id, String.CASE_INSENSITIVE_ORDER);
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

    private static String uri(java.net.URI value) { return value == null ? "" : value.toString(); }
    private static String image(MarketPaint.ImageReference value) {
        return present(value.path()) ? value.path() : uri(value.sourceUrl());
    }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
