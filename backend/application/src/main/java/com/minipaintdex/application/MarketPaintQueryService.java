package com.minipaintdex.application;

import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.query.SortOrder;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.PaintFacetValue;
import com.minipaintdex.application.view.PaintFacetView;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.domain.market.paint.MarketPaint;
import com.minipaintdex.domain.shared.DomainException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
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
        var query = string(filters.query()).toLowerCase(Locale.ROOT);
        return catalogs.load().paints().stream().map(MarketPaintQueryService::view).filter(paint -> {
            var profile = paint.profile();
            if (!matches(filters.brand(), paint.brand())) return false;
            if (!matches(filters.range(), paint.range())) return false;
            if (!contains(filters.role(), profile.roles())) return false;
            if (!contains(filters.applicationMethod(), profile.applicationMethods())) return false;
            if (!matches(filters.applicationSystem(), profile.applicationSystem())) return false;
            if (!matches(filters.color(), paint.colorFamily())) return false;
            if (!matches(filters.finish(), profile.finish())) return false;
            if (!matches(filters.medium(), profile.medium())) return false;
            if (!matches(filters.coverage(), profile.coverage())) return false;
            if (!contains(filters.effect(), profile.effects())) return false;
            if (!matches(filters.undercoat(), profile.undercoatTone())) return false;
            if (!matches(filters.lifecycle(), paint.lifecycleStatus())) return false;
            if (!query.isBlank()) {
                var haystack = String.join(" ", paint.name(), paint.brand(), paint.manufacturer(), paint.range(),
                        paint.reference(), paint.colorFamily(), String.join(" ", paint.tags()),
                        String.join(" ", profile.roles()), String.join(" ", profile.effects()),
                        profile.applicationSystem(), profile.coverage(), profile.finish(), profile.medium())
                        .toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) return false;
            }
            return true;
        });
    }

    PageResult<MarketPaintView> page(
            SearchMarketPaintsQuery filters,
            boolean manufacturerSheetOnly,
            boolean realResultOnly,
            PageQuery page) {
        var filtered = search(filters).stream()
                .filter(paint -> !manufacturerSheetOnly || present(paint.manufacturerUrl()))
                .filter(paint -> !realResultOnly || present(paint.resultImage())
                        || present(paint.resultReferenceUrl()))
                .sorted(comparator(page.sort())).toList();
        var from = Math.min(page.offset(), filtered.size());
        var to = Math.min(from + page.size(), filtered.size());
        return new PageResult<>(filtered.subList(from, to), page.page(), page.size(), filtered.size());
    }

    PaintFacetsView facets(SearchMarketPaintsQuery filters) {
        return facets(search(filters));
    }

    static PaintFacetsView facets(List<MarketPaintView> paints) {
        return new PaintFacetsView(paints.size(), List.of(
                facet("roles", paints, paint -> paint.profile().roles()),
                facet("applicationMethods", paints, paint -> paint.profile().applicationMethods()),
                facet("applicationSystems", paints, paint -> List.of(paint.profile().applicationSystem())),
                facet("colors", paints, paint -> List.of(paint.colorFamily())),
                facet("brands", paints, paint -> List.of(paint.brand())),
                facet("ranges", paints, paint -> List.of(paint.range())),
                facet("coverages", paints, paint -> List.of(paint.profile().coverage())),
                facet("finishes", paints, paint -> List.of(paint.profile().finish())),
                facet("effects", paints, paint -> paint.profile().effects()),
                facet("undercoats", paints, paint -> List.of(paint.profile().undercoatTone())),
                facet("mediums", paints, paint -> List.of(paint.profile().medium())),
                facet("lifecycles", paints, paint -> List.of(paint.lifecycleStatus()))));
    }

    List<MarketPaintView> views(MarketCatalogSnapshot snapshot) {
        return snapshot.paints().stream().map(MarketPaintQueryService::view)
                .sorted(Comparator.comparing(MarketPaintView::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static MarketPaintView view(MarketPaint paint) {
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
                string(paint.manufacturerImage().credit()), paint.volumeMl(), string(paint.color().family()),
                string(paint.notes()), paint.recommendedUses(),
                new MarketPaintView.UsageInstructions(
                        string(usage.summary()), usage.steps(), usage.tips(),
                        usage.instructionStatus() == null && technical
                                ? "legacy_unverified" : string(usage.instructionStatus()),
                        usage.reviewRequired() || technical && usage.instructionStatus() == null),
                verifiedAt, image(paint.resultImage()), string(paint.resultImage().credit()),
                uri(paint.resultImage().sourceUrl()), string(paint.resultImage().license()),
                uri(paint.resultImage().referenceUrl()));
    }

    private static Comparator<MarketPaintView> comparator(List<SortOrder> orders) {
        var effective = orders.isEmpty() ? List.of(new SortOrder("name", SortOrder.Direction.ASCENDING)) : orders;
        Comparator<MarketPaintView> comparator = null;
        for (var order : effective) {
            if (!java.util.Set.of("name", "brand", "range", "reference", "role", "colorFamily")
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

    private static PaintFacetView facet(
            String id,
            List<MarketPaintView> paints,
            Function<MarketPaintView, List<String>> values) {
        var counts = new java.util.TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
        paints.forEach(paint -> values.apply(paint).stream().filter(MarketPaintQueryService::present)
                .forEach(value -> counts.merge(value, 1, Integer::sum)));
        return new PaintFacetView(id, counts.entrySet().stream()
                .map(entry -> new PaintFacetValue(entry.getKey(), entry.getValue())).toList());
    }

    private static String sortableValue(MarketPaintView value, String property) {
        return switch (property) {
            case "name" -> value.name();
            case "brand" -> value.brand();
            case "range" -> value.range();
            case "reference" -> value.reference();
            case "role" -> value.profile().roles().getFirst();
            case "colorFamily" -> value.colorFamily();
            default -> throw new IllegalArgumentException("Unsupported paint sort property: " + property);
        };
    }

    private static boolean contains(String expected, List<String> actual) {
        return !present(expected) || actual.stream().anyMatch(value -> value.equalsIgnoreCase(expected));
    }

    private static boolean matches(String expected, Object actual) {
        return !present(expected) || expected.equalsIgnoreCase(string(actual));
    }

    private static String uri(java.net.URI value) { return value == null ? "" : value.toString(); }
    private static String image(MarketPaint.ImageReference value) {
        return present(value.path()) ? value.path() : uri(value.sourceUrl());
    }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
