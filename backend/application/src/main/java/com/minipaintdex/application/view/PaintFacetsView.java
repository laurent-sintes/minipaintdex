package com.minipaintdex.application.view;

import java.util.List;

/** Complete set of filter facets for the current market-paint selection. */
public record PaintFacetsView(
        int total,
        List<PaintFacetValue> types,
        List<PaintFacetValue> colors,
        List<PaintFacetValue> brands,
        List<PaintFacetValue> manufacturers,
        List<PaintFacetValue> ranges,
        List<PaintFacetValue> finishes,
        List<PaintFacetValue> mediums,
        List<PaintFacetValue> opacities,
        List<PaintFacetValue> lifecycles,
        List<PaintFacetValue> volumes,
        List<PaintFacetValue> tags) {
    public PaintFacetsView {
        types = List.copyOf(types);
        colors = List.copyOf(colors);
        brands = List.copyOf(brands);
        manufacturers = List.copyOf(manufacturers);
        ranges = List.copyOf(ranges);
        finishes = List.copyOf(finishes);
        mediums = List.copyOf(mediums);
        opacities = List.copyOf(opacities);
        lifecycles = List.copyOf(lifecycles);
        volumes = List.copyOf(volumes);
        tags = List.copyOf(tags);
    }
}
