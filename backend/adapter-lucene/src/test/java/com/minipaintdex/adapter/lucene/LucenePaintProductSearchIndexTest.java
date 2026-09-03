package com.minipaintdex.adapter.lucene;

import com.minipaintdex.application.MarketCatalogApplicationService;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.query.*;
import com.minipaintdex.domain.market.paint.*;
import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class LucenePaintProductSearchIndexTest {
    private final PaintSearchPolicy policy = new PaintSearchPolicy(8, 20, 200, 16, 5, 1, 50, 2000, 1000, 8, 3, 1, 0.8f, 0.2f);
    private final List<PaintProduct> paints = List.of(
            paint("karak", "Karak Stone", "22-17", "Citadel", "Layer"),
            paint("karak-air", "Karak Stone", "28-17", "Citadel", "Air"),
            paint("celestra", "Celestra Grey", "21-03", "Citadel", "Base"),
            paint("blue", "Bleu Électrique", "70.950", "Vallejo", "Model Color"),
            paint("blue-air", "Bleu Électrique Air", "71.950", "Vallejo", "Model Air"));

    @Test void searchesPrefixesMultipleFieldsAccentsAndAliasesWithoutFuzzyReferences() throws Exception {
        try (var index = new LucenePaintProductSearchIndex(policy)) {
            assertEquals(List.of("karak", "karak-air"), index.rank(paints, "kar sto"));
            assertEquals(List.of("karak-air"), index.rank(paints, "citadel kar air"));
            assertEquals("blue", index.rank(paints, "BLEU ELECTRIQUE").getFirst());
            assertEquals(List.of("celestra"), index.rank(paints, "celestra gre"));
            assertEquals(List.of("celestra"), index.rank(paints, "celestr grey"));
            assertEquals(List.of("blue"), index.rank(paints, "70950"));
            assertEquals(List.of("blue"), index.rank(paints, "70.950"));
            assertTrue(index.rank(paints, "70951").isEmpty());
            assertTrue(index.rank(paints, "karak green").isEmpty());
            assertTrue(index.rank(paints, "++ : *").isEmpty());
            assertEquals(5, index.rank(paints, "").size());
            assertEquals(5, index.rank(paints, "brand-alias").size());
            assertTrue(index.rank(paints, "private-note-secret").isEmpty());
        }
    }

    @Test void prefersExactReferenceThenExactNameThenPrefixOverTypo() throws Exception {
        var products = List.of(paint("ref", "Other", "stone", "A", "R"),
                paint("name", "Stone", "1", "A", "R"),
                paint("prefix", "Stonewall", "2", "A", "R"),
                paint("typo", "Stony", "3", "A", "R"));
        try (var index = new LucenePaintProductSearchIndex(policy)) {
            assertEquals(List.of("ref", "name", "prefix", "typo"), index.rank(products, "stone"));
        }
    }

    @Test void replacesCompleteGenerationsIncludingChangedAndRemovedProducts() throws Exception {
        try (var index = new LucenePaintProductSearchIndex(policy)) {
            assertEquals(2, index.rank(paints, "karak").size());
            var changed = List.of(paint("karak", "Desert Sand", "22-17", "Citadel", "Layer"));
            assertTrue(index.rank(changed, "karak").isEmpty());
            assertEquals(List.of("karak"), index.rank(changed, "desert").stream().toList());
            assertEquals(List.of(), index.rank(List.of(), "desert"));
            assertEquals(2, index.rank(paints, "karak").size());
        }
    }

    @Test void concurrentSearchesNeverMixTheirSuppliedGenerations() throws Exception {
        try (var index = new LucenePaintProductSearchIndex(policy); var executor = Executors.newFixedThreadPool(4)) {
            var tasks = java.util.stream.IntStream.range(0, 24).mapToObj(i -> (java.util.concurrent.Callable<List<String>>) () ->
                    index.rank(i % 2 == 0 ? paints : List.of(paints.get(2)), "")).toList();
            var results = executor.invokeAll(tasks);
            for (int i = 0; i < results.size(); i++) assertEquals(i % 2 == 0 ? 5 : 1, results.get(i).get().size());
        }
    }

    @Test void boundsInputAndClosesIdempotently() throws Exception {
        var index = new LucenePaintProductSearchIndex(policy);
        try {
            assertEquals("invalid_input", assertThrows(DomainException.class, () -> index.rank(paints, "x".repeat(201))).code());
            assertEquals("invalid_input", assertThrows(DomainException.class, () -> index.rank(paints, "a ".repeat(17))).code());
        } finally { index.close(); }
        index.close();
        assertEquals("search_unavailable", assertThrows(DomainException.class, () -> index.rank(paints, "karak")).code());
    }

    @Test void suggestionsPagesAndFacetsUseTheSameMatchesAndFilteredRanking() throws Exception {
        try (var index = new LucenePaintProductSearchIndex(policy)) {
            var market = new MarketCatalogApplicationService(() -> new MarketCatalogSnapshot(paints, List.of(), List.of(), List.of(), java.util.List.of()), index, policy);
            var filters = SearchPaintProductsQuery.fromSelections("karak", null, List.of("Citadel::Air"), null, null, null, null, null, null, null, null, null, null);
            var suggestions = market.searchPaintProducts(new PaintSearchQuery(filters, false, false, java.util.Set.of("suggestions"), null, 1, "test"));
            assertEquals(List.of("karak-air"), suggestions.suggestions().stream().map(s -> s.paintProductId()).toList());
            assertEquals("test", suggestions.correlationId());
            var page = market.searchPaintProducts(new com.minipaintdex.application.query.PaintSearchQuery(filters, false, false, java.util.Set.of("results"), new PageQuery(0, 10, List.of()), null, "test")).results();
            assertEquals(List.of("karak-air"), page.content().stream().map(p -> p.id()).toList());
            var facets = market.paintProductFacets(filters, false, false);
            assertEquals(page.totalElements(), facets.total());
            assertEquals(2, facets.facets().stream().filter(f -> f.id().equals("ranges")).findFirst().orElseThrow()
                    .values().stream().mapToInt(f -> f.count()).sum());
            assertTrue(market.searchPaintProducts(new PaintSearchQuery(SearchPaintProductsQuery.empty(), false, false, java.util.Set.of("suggestions"), null, null, "test")).suggestions().isEmpty());
            assertThrows(DomainException.class, () -> market.searchPaintProducts(new PaintSearchQuery(filters, false, false, java.util.Set.of("suggestions"), null, 21, "test")));
            assertThrows(DomainException.class, () -> market.searchPaintProducts(new com.minipaintdex.application.query.PaintSearchQuery(filters, false, false, java.util.Set.of("results"), new PageQuery(0, 5, List.of(new SortOrder("bad", SortOrder.Direction.ASCENDING))), null, "test")).results());
        }
    }

    private static PaintProduct paint(String id, String name, String reference, String brand, String range) {
        var profile = new PaintProductProfile(List.of(PaintProductProfile.Role.COLOR_PAINT), List.of(PaintProductProfile.ApplicationMethod.BRUSH),
                PaintProductProfile.ApplicationSystem.CONVENTIONAL_LAYERING, PaintProductProfile.Coverage.OPAQUE,
                PaintProductProfile.Finish.MATTE, List.of(), new PaintProductProfile.Undercoat(PaintProductProfile.UndercoatTone.ANY, false), PaintProductProfile.Medium.ACRYLIC);
        return new PaintProduct(1, id, brand, brand, List.of("brand-alias"), range, profile, reference, name,
                new PaintProduct.Color("grey", null), PaintProductLifecycle.ACTIVE, "confirmed", List.of(), List.of(), "private-note-secret",
                null, new PaintProduct.ImageReference(null, java.net.URI.create("https://example.org/paint.jpg"), "Manufacturer",
                        null, null, PaintProductImageQuality.OFFICIAL_PHOTO, java.time.LocalDate.of(2026, 1, 1), null),
                18, List.of(), null, null, null, List.of(), java.util.List.of());
    }
}
