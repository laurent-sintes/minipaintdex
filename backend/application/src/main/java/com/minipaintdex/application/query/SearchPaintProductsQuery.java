package com.minipaintdex.application.query;

import java.util.List;

/** OR within each facet, AND across facets; whole brands and qualified ranges form one OR group. */
public record SearchPaintProductsQuery(
        String query,
        List<String> brand,
        List<PaintRangeSelection> range,
        List<String> role,
        List<String> applicationMethod,
        List<String> applicationSystem,
        List<String> color,
        List<String> finish,
        List<String> medium,
        List<String> coverage,
        List<String> effect,
        List<String> undercoat,
        List<String> lifecycle) {

    public SearchPaintProductsQuery {
        query = query == null ? "" : query.trim();
        brand = values(brand); range = range == null ? List.of() : List.copyOf(range);
        role = values(role); applicationMethod = values(applicationMethod);
        applicationSystem = values(applicationSystem); color = values(color);
        finish = values(finish); medium = values(medium); coverage = values(coverage);
        effect = values(effect); undercoat = values(undercoat); lifecycle = values(lifecycle);
    }

    public static SearchPaintProductsQuery fromSelections(
            String query, List<String> brand, List<String> range, List<String> role,
            List<String> applicationMethod, List<String> applicationSystem, List<String> color,
            List<String> finish, List<String> medium, List<String> coverage, List<String> effect,
            List<String> undercoat, List<String> lifecycle) {
        return new SearchPaintProductsQuery(query, brand, values(range).stream().map(PaintRangeSelection::parse).toList(),
                role, applicationMethod, applicationSystem, color, finish, medium, coverage, effect, undercoat, lifecycle);
    }

    public static SearchPaintProductsQuery empty() {
        return new SearchPaintProductsQuery(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static List<String> values(List<String> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull)
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    }
}
