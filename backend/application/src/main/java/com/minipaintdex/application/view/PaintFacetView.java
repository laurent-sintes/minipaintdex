package com.minipaintdex.application.view;

import java.util.List;

/** One generic facet identified by the published paint-model metadata. */
public record PaintFacetView(String id, List<PaintFacetValue> values) {
    public PaintFacetView { values = List.copyOf(values); }
}
