package com.minipaintdex.application;

import com.minipaintdex.application.query.PaintRangeSelection;
import com.minipaintdex.application.query.SearchPaintProductsQuery;
import com.minipaintdex.application.view.PaintProductView;
import com.minipaintdex.application.view.PaintFacetValue;
import com.minipaintdex.application.view.PaintFacetView;
import com.minipaintdex.application.view.PaintFacetsView;

import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;

/** Shared read-only filtering for market references and workshop-owned references. */
final class PaintSearch {
    private PaintSearch() {}

    static boolean matches(PaintProductView paint, SearchPaintProductsQuery query) {
        return matches(paint, query, "");
    }

    private static boolean matches(PaintProductView paint, SearchPaintProductsQuery query, String excludedFacet) {
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
        return true;
    }

    static PaintFacetsView facets(List<PaintProductView> paints, SearchPaintProductsQuery query, java.util.Set<String> textMatches) {
        return new PaintFacetsView((int) paints.stream().filter(paint -> textMatches.contains(paint.id()) && matches(paint, query)).count(),
                java.util.Arrays.stream(Facet.values()).map(facet -> {
                    var counts = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
                    var samples = new TreeMap<String, PaintProductView>(String.CASE_INSENSITIVE_ORDER);
                    for (var paint : paints) {
                        var matches = textMatches.contains(paint.id()) && matches(paint, query, facet.id);
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


    static List<PaintProductView> order(List<PaintProductView> ranked, List<com.minipaintdex.application.query.SortOrder> orders) {
        if (orders.isEmpty()) return ranked;
        var ranks = new java.util.HashMap<String, Integer>();
        for (int i = 0; i < ranked.size(); i++) ranks.put(ranked.get(i).id(), i);
        java.util.Comparator<PaintProductView> comparator = null;
        for (var order : orders) {
            java.util.Comparator<PaintProductView> next = "relevance".equals(order.property())
                    ? java.util.Comparator.comparingInt(p -> -ranks.get(p.id()))
                    : java.util.Comparator.comparing(p -> sortable(p, order.property()), String.CASE_INSENSITIVE_ORDER);
            // Validate even an empty result, before Comparator has a chance to access a row.
            if (!java.util.Set.of("relevance", "name", "brand", "range", "reference", "role", "colorFamily", "verifiedAt").contains(order.property()))
                throw new com.minipaintdex.domain.shared.DomainException("invalid_input", "Unsupported paint sort property: " + order.property());
            if (order.direction() == com.minipaintdex.application.query.SortOrder.Direction.DESCENDING) next = next.reversed();
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        return ranked.stream().sorted(comparator.thenComparing(PaintProductView::id)).toList();
    }

    private static String sortable(PaintProductView paint, String field) {
        return switch (field) {
            case "name" -> paint.name();
            case "brand" -> paint.brand();
            case "range" -> paint.range();
            case "reference" -> paint.reference();
            case "role" -> paint.profile().roles().getFirst();
            case "colorFamily" -> paint.colorFamily();
            case "verifiedAt" -> paint.manufacturerVerifiedAt();
            default -> throw new IllegalArgumentException("Unsupported paint sort: " + field);
        };
    }

    private enum Facet {
        BRANDS("brands", paint -> List.of(paint.brand()), null),
        RANGES("ranges", paint -> List.of(new PaintRangeSelection(paint.brand(), paint.range()).selectionKey()), null),
        ROLES("roles", paint -> paint.profile().roles(), SearchPaintProductsQuery::role),
        METHODS("applicationMethods", paint -> paint.profile().applicationMethods(), SearchPaintProductsQuery::applicationMethod),
        SYSTEMS("applicationSystems", paint -> List.of(paint.profile().applicationSystem()), SearchPaintProductsQuery::applicationSystem),
        COLORS("colors", paint -> List.of(paint.colorFamily()), SearchPaintProductsQuery::color),
        COVERAGES("coverages", paint -> List.of(paint.profile().coverage()), SearchPaintProductsQuery::coverage),
        FINISHES("finishes", paint -> List.of(paint.profile().finish()), SearchPaintProductsQuery::finish),
        EFFECTS("effects", paint -> paint.profile().effects(), SearchPaintProductsQuery::effect),
        UNDERCOATS("undercoats", paint -> List.of(paint.profile().undercoatTone()), SearchPaintProductsQuery::undercoat),
        MEDIUMS("mediums", paint -> List.of(paint.profile().medium()), SearchPaintProductsQuery::medium),
        LIFECYCLES("lifecycles", paint -> List.of(paint.lifecycleStatus()), SearchPaintProductsQuery::lifecycle);

        private final String id;
        private final Function<PaintProductView, List<String>> values;
        private final Function<SearchPaintProductsQuery, List<String>> selections;

        Facet(String id, Function<PaintProductView, List<String>> values,
                Function<SearchPaintProductsQuery, List<String>> selections) {
            this.id = id; this.values = values; this.selections = selections;
        }
    }
}
