package com.minipaintdex.application;

import com.minipaintdex.application.query.PaintRangeSelection;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.PaintFacetValue;
import com.minipaintdex.application.view.PaintFacetView;
import com.minipaintdex.application.view.PaintFacetsView;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.function.Function;

/** Shared read-only filtering for market references and workshop-owned references. */
final class PaintSearch {
    private PaintSearch() {}

    static boolean matches(MarketPaintView paint, SearchMarketPaintsQuery query) {
        return matches(paint, query, "");
    }

    private static boolean matches(MarketPaintView paint, SearchMarketPaintsQuery query, String excludedFacet) {
        if (!excludedFacet.equals("brands") && !excludedFacet.equals("ranges")
                && (!query.brand().isEmpty() || !query.range().isEmpty())
                && query.brand().stream().noneMatch(brand -> same(brand, paint.brand()))
                && query.range().stream().noneMatch(range -> same(range.brand(), paint.brand())
                        && same(range.range(), paint.range()))) return false;
        for (var facet : Facet.values()) {
            if (facet.selections == null || facet.id.equals(excludedFacet)) continue;
            var selected = facet.selections.apply(query);
            if (!selected.isEmpty() && selected.stream().noneMatch(expected ->
                    facet.values.apply(paint).stream().anyMatch(actual -> same(expected, actual)))) return false;
        }
        if (query.query().isBlank()) return true;
        var profile = paint.profile();
        var haystack = String.join(" ", paint.name(), paint.brand(), paint.manufacturer(), paint.range(),
                paint.reference(), paint.colorFamily(), String.join(" ", paint.tags()),
                String.join(" ", profile.roles()), String.join(" ", profile.applicationMethods()),
                String.join(" ", profile.effects()), profile.applicationSystem(), profile.coverage(),
                profile.finish(), profile.medium());
        var normalized = normalize(haystack);
        return java.util.Arrays.stream(normalize(query.query()).split("\\s+")).allMatch(normalized::contains);
    }

    static PaintFacetsView facets(List<MarketPaintView> paints, SearchMarketPaintsQuery query) {
        return new PaintFacetsView((int) paints.stream().filter(paint -> matches(paint, query)).count(),
                java.util.Arrays.stream(Facet.values()).map(facet -> {
                    var counts = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
                    var samples = new TreeMap<String, MarketPaintView>(String.CASE_INSENSITIVE_ORDER);
                    for (var paint : paints) {
                        var matches = matches(paint, query, facet.id);
                        for (var value : facet.values.apply(paint).stream().filter(v -> !v.isBlank()).distinct().toList()) {
                            counts.merge(value, matches ? 1 : 0, Integer::sum);
                            samples.putIfAbsent(value, paint);
                        }
                    }
                    return new PaintFacetView(facet.id, counts.entrySet().stream().map(entry -> {
                        var sample = samples.get(entry.getKey());
                        return new PaintFacetValue(entry.getKey(), entry.getValue(),
                                facet == Facet.RANGES ? sample.range() : entry.getKey(),
                                facet == Facet.RANGES ? sample.brand() : null);
                    }).toList());
                }).toList());
    }

    private static boolean same(String left, String right) { return left.equalsIgnoreCase(right); }
    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private enum Facet {
        BRANDS("brands", paint -> List.of(paint.brand()), null),
        RANGES("ranges", paint -> List.of(new PaintRangeSelection(paint.brand(), paint.range()).selectionKey()), null),
        ROLES("roles", paint -> paint.profile().roles(), SearchMarketPaintsQuery::role),
        METHODS("applicationMethods", paint -> paint.profile().applicationMethods(), SearchMarketPaintsQuery::applicationMethod),
        SYSTEMS("applicationSystems", paint -> List.of(paint.profile().applicationSystem()), SearchMarketPaintsQuery::applicationSystem),
        COLORS("colors", paint -> List.of(paint.colorFamily()), SearchMarketPaintsQuery::color),
        COVERAGES("coverages", paint -> List.of(paint.profile().coverage()), SearchMarketPaintsQuery::coverage),
        FINISHES("finishes", paint -> List.of(paint.profile().finish()), SearchMarketPaintsQuery::finish),
        EFFECTS("effects", paint -> paint.profile().effects(), SearchMarketPaintsQuery::effect),
        UNDERCOATS("undercoats", paint -> List.of(paint.profile().undercoatTone()), SearchMarketPaintsQuery::undercoat),
        MEDIUMS("mediums", paint -> List.of(paint.profile().medium()), SearchMarketPaintsQuery::medium),
        LIFECYCLES("lifecycles", paint -> List.of(paint.lifecycleStatus()), SearchMarketPaintsQuery::lifecycle);

        private final String id;
        private final Function<MarketPaintView, List<String>> values;
        private final Function<SearchMarketPaintsQuery, List<String>> selections;

        Facet(String id, Function<MarketPaintView, List<String>> values,
                Function<SearchMarketPaintsQuery, List<String>> selections) {
            this.id = id; this.values = values; this.selections = selections;
        }
    }
}
