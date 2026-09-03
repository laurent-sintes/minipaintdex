package com.minipaintdex.application;

import com.minipaintdex.application.query.PaintRangeSelection;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PaintSearchTest {
    private final List<MarketPaintView> paints = List.of(
            paint("a-blue", "Alpha", "Brush", "blue", "brush"),
            paint("a-red", "Alpha", "Air", "red", "airbrush"),
            paint("b-blue", "Beta", "Brush", "blue", "brush"),
            paint("b-green", "Beta", "Air", "green", "airbrush"));

    @Test
    void combinesOrWithinFacetsAndBetweenFacets() {
        var query = query(List.of("Alpha", "Beta"), null, List.of("blue", "red"), List.of("airbrush"));
        assertEquals(List.of("a-red"), matching(query));
    }

    @Test
    void combinesWholeBrandsAndQualifiedRangesWithoutCrossBrandMatches() {
        assertEquals(List.of("a-blue", "a-red", "b-green"),
                matching(query(List.of("Alpha"), List.of("Beta::Air"), null, null)));
        assertEquals(List.of("b-blue"), matching(query(null, List.of("Beta::Brush"), null, null)));
        assertEquals(List.of("a-red", "b-blue"), matching(query(null, List.of("Alpha::Air", "Beta::Brush"), null, null)));
    }

    @Test
    void countsAlternativesIgnoringOnlyTheirOwnFacetAndTheCombinedCatalogGroup() {
        var query = query(List.of("Alpha"), null, List.of("blue"), null);
        var facets = PaintSearch.facets(paints, query);
        assertEquals(1, facets.total());
        assertEquals(Map.of("blue", 1, "red", 1, "green", 0), counts(facets, "colors"));
        assertEquals(Map.of("Alpha", 1, "Beta", 1), counts(facets, "brands"));
        assertEquals(Map.of("Alpha::Brush", 1, "Alpha::Air", 0, "Beta::Brush", 1, "Beta::Air", 0), counts(facets, "ranges"));
        assertEquals(Map.of("brush", 1, "airbrush", 0), counts(facets, "applicationMethods"));
        var range = facets.facets().stream().filter(f -> f.id().equals("ranges")).findFirst().orElseThrow().values().getFirst();
        assertEquals("Alpha", range.parentValue());
        assertEquals("Air", range.label());
    }

    @Test
    void retainsZeroCountOptionsAndHonorsTextSearch() {
        var query = SearchMarketPaintsQuery.fromSelections("  BÉTA b-blue ", null, null, null, null, null,
                List.of("red"), null, null, null, null, null, null);
        var facets = PaintSearch.facets(paints, query);
        assertEquals(0, facets.total());
        assertEquals(Map.of("blue", 1, "red", 0, "green", 0), counts(facets, "colors"));
    }

    @Test
    void roundTripsEscapedRangeNamesAndRejectsAmbiguousSelections() {
        var range = new PaintRangeSelection("Brand: Studio", "Air\\Brush::Special");
        assertEquals(range, PaintRangeSelection.parse(range.selectionKey()));
        for (var invalid : List.of("Brush", "::Air", "Alpha::", "Alpha::Air::Brush", "Alpha::Air\\")) {
            assertThrows(DomainException.class, () -> PaintRangeSelection.parse(invalid), invalid);
        }
    }

    @Test
    void normalizesSelectionsAndDefensivelyCopiesLists() {
        var selected = new java.util.ArrayList<>(List.of(" blue ", "", "blue"));
        var query = query(null, null, selected, null);
        selected.clear();
        assertEquals(List.of("blue"), query.color());
        assertThrows(UnsupportedOperationException.class, () -> query.color().add("red"));
    }

    private List<String> matching(SearchMarketPaintsQuery query) {
        return paints.stream().filter(paint -> PaintSearch.matches(paint, query)).map(MarketPaintView::id).toList();
    }

    private static Map<String, Integer> counts(PaintFacetsView facets, String id) {
        return facets.facets().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow().values().stream()
                .collect(Collectors.toMap(value -> value.value(), value -> value.count()));
    }

    private static SearchMarketPaintsQuery query(List<String> brands, List<String> ranges, List<String> colors, List<String> methods) {
        return SearchMarketPaintsQuery.fromSelections("", brands, ranges, null, methods, null, colors, null, null, null, null, null, null);
    }

    private static MarketPaintView paint(String id, String brand, String range, String color, String method) {
        return new MarketPaintView(id, brand, brand, List.of(), range,
                new MarketPaintView.Profile(List.of("color_paint"), List.of(method), "conventional_layering",
                        "opaque", "matte", List.of(), "any", false, "water_based_acrylic"),
                id, "Paint", "#000000", "active", "confirmed", "", List.of(),
                "", "", "", "", "", "", "", "none", 6, "", "", "", "", 18, color, "", List.of(),
                new MarketPaintView.UsageInstructions("", List.of(), List.of(), "", false),
                "", "", "", "", "", "", List.of());
    }
}
