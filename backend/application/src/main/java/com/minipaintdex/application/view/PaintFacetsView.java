package com.minipaintdex.application.view;

import java.util.List;

/** Complete set of filter facets for the current market-paint selection. */
public record PaintFacetsView(
        int total,
        List<PaintFacetView> facets) {
    public PaintFacetsView {
        facets = List.copyOf(facets);
    }
}
