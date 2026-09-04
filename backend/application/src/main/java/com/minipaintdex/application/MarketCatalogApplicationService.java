package com.minipaintdex.application;

import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchPaintProductsQuery;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.view.PaintProductView;
import com.minipaintdex.application.view.MarketPaintingGuideView;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.application.view.PaintCatalogQualityView;
import com.minipaintdex.application.view.PaintModelView;
import com.minipaintdex.application.view.PaintableProductSummaryView;
import com.minipaintdex.application.view.PaintableProductView;

import java.util.List;
import com.minipaintdex.domain.market.storage.*;
import com.minipaintdex.application.query.RackCatalogQuery;
import com.minipaintdex.application.query.GetRackReferenceQuery;
import com.minipaintdex.application.result.RackCatalogPage;
import java.util.Objects;
import java.util.stream.Stream;
import com.minipaintdex.domain.market.paint.PaintProductLifecycle;
import com.minipaintdex.domain.market.paint.PaintProductImageQuality;
import com.minipaintdex.domain.market.paint.PaintProductImageLimitationCode;
import com.minipaintdex.domain.market.paint.PaintProductProfile;

/** Cohesive read service for the market knowledge bounded context. */
public final class MarketCatalogApplicationService implements MarketCatalogUseCases {
    @Override public RackCatalogPage<RackProduct> searchRackProducts(RackCatalogQuery query) {
        var catalog = catalogs.load().rackCatalog();
        return rackPage(catalog.rackProducts().stream().sorted(java.util.Comparator.comparing(RackProduct::id))
                .filter(value -> rackMatches(value.name() + " " + value.brand(), query.query())).toList(), catalog.revision(), query);
    }
    @Override public RackCatalogPage<PaintContainerFormat> searchContainerFormats(RackCatalogQuery query) {
        var catalog = catalogs.load().rackCatalog();
        return rackPage(catalog.containerFormats().stream().sorted(java.util.Comparator.comparing(PaintContainerFormat::id))
                .filter(value -> rackMatches(value.name() + " " + value.brand(), query.query())).toList(), catalog.revision(), query);
    }
    @Override public RackProduct getRackProduct(GetRackReferenceQuery query) {
        return catalogs.load().rackCatalog().rackProducts().stream().filter(value -> value.id().equals(query.id())).findFirst()
                .orElseThrow(() -> new com.minipaintdex.domain.shared.DomainException("not_found", "Rack product not found."));
    }
    @Override public PaintContainerFormat getContainerFormat(GetRackReferenceQuery query) {
        return catalogs.load().rackCatalog().containerFormats().stream().filter(value -> value.id().equals(query.id())).findFirst()
                .orElseThrow(() -> new com.minipaintdex.domain.shared.DomainException("not_found", "Container format not found."));
    }
    private static boolean rackMatches(String text, String query) {
        return text.toLowerCase(java.util.Locale.ROOT).contains(Objects.toString(query, "").toLowerCase(java.util.Locale.ROOT));
    }
    private static <T> RackCatalogPage<T> rackPage(List<T> values, long revision, RackCatalogQuery query) {
        if (!query.page().sort().isEmpty()) throw new com.minipaintdex.domain.shared.DomainException("invalid_input", "Use stable identity ordering.");
        var from = Math.min(query.page().offset(), values.size());
        return new RackCatalogPage<>(new PageResult<>(values.subList(from, Math.min(from + query.page().size(), values.size())),
                query.page().page(), query.page().size(), values.size()), revision, query.correlationId());
    }
    private final PaintProductQueryService paints;
    private final MarketPaintableProductQueryService products;
    private final MarketCatalogReader catalogs;
    private final com.minipaintdex.application.query.PaintSearchPolicy searchPolicy;

    public MarketCatalogApplicationService(MarketCatalogReader catalogs,
            com.minipaintdex.application.port.PaintProductSearchIndex index,
            com.minipaintdex.application.query.PaintSearchPolicy searchPolicy) {
        this.searchPolicy = Objects.requireNonNull(searchPolicy);
        var reader = Objects.requireNonNull(catalogs);
        this.catalogs = reader;
        this.paints = new PaintProductQueryService(reader, index);
        this.products = new MarketPaintableProductQueryService(reader, paints);
    }

    @Override public com.minipaintdex.application.result.PaintSearchResult<PaintProductView> searchPaintProducts(
            com.minipaintdex.application.query.PaintSearchQuery query) {
        return paints.search(query, searchPolicy);
    }

    @Override public List<PaintProductView> searchPaintProducts(SearchPaintProductsQuery query) {
        return paints.search(query);
    }

    @Override public PageResult<com.minipaintdex.domain.market.paint.PaintCatalogEdition> searchPaintCatalogEditions(
            com.minipaintdex.application.query.SearchPaintCatalogEditionsQuery query) {
        Objects.requireNonNull(query);
        var comparator = java.util.Comparator.comparing(com.minipaintdex.domain.market.paint.PaintCatalogEdition::id);
        if (query.page().sort().size() > 1 || query.page().sort().stream().anyMatch(order -> !"id".equals(order.property()))) {
            throw new com.minipaintdex.domain.shared.DomainException("invalid_input", "Catalog editions support id sorting only");
        }
        if (!query.page().sort().isEmpty() && query.page().sort().getFirst().direction()
                == com.minipaintdex.application.query.SortOrder.Direction.DESCENDING) comparator = comparator.reversed();
        var rows = catalogs.load().paintCatalogEditions().stream()
                .filter(edition -> query.brand() == null || query.brand().isBlank() || edition.brand().equalsIgnoreCase(query.brand()))
                .sorted(comparator).toList();
        var start = Math.min(query.page().offset(), rows.size());
        return new PageResult<>(rows.subList(start, Math.min(start + query.page().size(), rows.size())),
                query.page().page(), query.page().size(), rows.size());
    }

    @Override public com.minipaintdex.domain.market.paint.PaintCatalogEdition getPaintCatalogEdition(
            com.minipaintdex.application.query.GetPaintCatalogEditionQuery query) {
        return catalogs.load().paintCatalogEditions().stream().filter(edition -> edition.id().equals(query.id()))
                .findFirst().orElseThrow(() -> new com.minipaintdex.domain.shared.DomainException("not_found", "Catalog edition not found: " + query.id()));
    }
    @Override public com.minipaintdex.application.result.PaintUsageGuidesResult searchPaintUsageGuides(
            com.minipaintdex.application.query.SearchPaintUsageGuidesQuery query) {
        return new PaintUsageGuideQueryService(catalogs).search(query);
    }
    @Override public com.minipaintdex.application.result.PaintUsageGuideResult getPaintUsageGuide(
            com.minipaintdex.application.query.GetPaintUsageGuideQuery query) {
        return new PaintUsageGuideQueryService(catalogs).get(query);
    }
    @Override public Stream<PaintProductView> streamPaintProducts(SearchPaintProductsQuery query) {
        return paints.stream(query);
    }
    @Override public PaintFacetsView paintProductFacets(
            SearchPaintProductsQuery filters, boolean manufacturerSheetOnly, boolean realResultOnly) {
        return paints.facets(filters, manufacturerSheetOnly, realResultOnly);
    }
    @Override public PaintModelView paintProductModel() {
        return new PaintModelView(1, "https://json-schema.org/draft/2020-12/schema", List.of(
                filter("role", "role", "roles", "collection.roleFilter", "paint-role", 1),
                filter("applicationMethod", "applicationMethod", "applicationMethods", "collection.applicationMethodFilter", "application-method", 2),
                filter("applicationSystem", "applicationSystem", "applicationSystems", "collection.applicationSystemFilter", "application-system", 3),
                filter("coverage", "coverage", "coverages", "collection.coverageFilter", "coverage", 4),
                filter("finish", "finish", "finishes", "collection.finishFilter", "finish", 5),
                filter("effect", "effect", "effects", "collection.effectFilter", "effect", 6),
                filter("undercoat", "undercoat", "undercoats", "collection.undercoatFilter", "undercoat-tone", 7),
                filter("medium", "medium", "mediums", "collection.mediumFilter", "medium", 8),
                filter("color", "color", "colors", "collection.colorFilter", null, 9),
                filter("brand", "brand", "brands", "collection.brandFilter", null, 10),
                filter("range", "range", "ranges", "collection.rangeFilter", null, 11),
                filter("lifecycle", "lifecycle", "lifecycles", "collection.lifecycleFilter", "lifecycle", 12),
                toggle("manufacturer-sheet", "manufacturerSheetOnly", "collection.manufacturerSheetOnly", 13),
                toggle("real-result", "realResultOnly", "collection.realResultOnly", 14)),
                List.of(
                        sort("relevance", "relevance,desc", "collection.sortRelevance", 0),
                        sort("name-ascending", "name,asc", "collection.sortNameAscending", 1),
                        sort("name-descending", "name,desc", "collection.sortNameDescending", 2),
                        sort("brand-ascending", "brand,asc", "collection.sortBrandAscending", 3),
                        sort("brand-descending", "brand,desc", "collection.sortBrandDescending", 4),
                        sort("range-ascending", "range,asc", "collection.sortRangeAscending", 5),
                        sort("range-descending", "range,desc", "collection.sortRangeDescending", 6),
                        sort("reference-ascending", "reference,asc", "collection.sortReferenceAscending", 7),
                        sort("reference-descending", "reference,desc", "collection.sortReferenceDescending", 8),
                        sort("verified-newest", "verifiedAt,desc", "collection.sortVerifiedNewest", 9),
                        sort("verified-oldest", "verifiedAt,asc", "collection.sortVerifiedOldest", 10)),
                List.of(
                        vocabulary("paint-role", java.util.Arrays.stream(PaintProductProfile.Role.values()).map(PaintProductProfile.Role::id).toList()),
                        vocabulary("application-method", java.util.Arrays.stream(PaintProductProfile.ApplicationMethod.values()).map(PaintProductProfile.ApplicationMethod::id).toList()),
                        vocabulary("application-system", java.util.Arrays.stream(PaintProductProfile.ApplicationSystem.values()).map(PaintProductProfile.ApplicationSystem::id).toList()),
                        vocabulary("coverage", java.util.Arrays.stream(PaintProductProfile.Coverage.values()).map(PaintProductProfile.Coverage::id).toList()),
                        vocabulary("finish", java.util.Arrays.stream(PaintProductProfile.Finish.values()).map(PaintProductProfile.Finish::id).toList()),
                        vocabulary("effect", java.util.Arrays.stream(PaintProductProfile.Effect.values()).map(PaintProductProfile.Effect::id).toList()),
                        vocabulary("undercoat-tone", java.util.Arrays.stream(PaintProductProfile.UndercoatTone.values()).map(PaintProductProfile.UndercoatTone::id).toList()),
                        vocabulary("medium", java.util.Arrays.stream(PaintProductProfile.Medium.values()).map(PaintProductProfile.Medium::id).toList()),
                        vocabulary("lifecycle", java.util.Arrays.stream(PaintProductLifecycle.values()).map(PaintProductLifecycle::id).toList()),
                        vocabulary("image-quality", java.util.Arrays.stream(PaintProductImageQuality.values()).map(PaintProductImageQuality::id).toList()),
                        vocabulary("image-quality-limitation", java.util.Arrays.stream(PaintProductImageLimitationCode.values())
                                .map(PaintProductImageLimitationCode::id).toList())));
    }
    @Override public PaintCatalogQualityView paintProductQuality() {
        return paints.quality();
    }
    @Override public PaintProductView getPaintProduct(String id) {
        return paints.get(id);
    }
    @Override public List<PaintableProductSummaryView> listMarketPaintableProducts() {
        return products.summaries();
    }
    @Override public PaintableProductView getMarketPaintableProduct(String paintableProductId) {
        return products.product(paintableProductId);
    }
    @Override public List<MarketPaintingGuideView> listMarketPaintingGuides(String paintableComponentId) {
        return products.guides(paintableComponentId);
    }
    @Override public String exportPaints(String format) {
        var results = paints.search(SearchPaintProductsQuery.empty());
        if ("csv".equals(format)) {
            var rows = new java.util.ArrayList<String>();
            rows.add("id,brand,range,reference,name,color_hex,color_family,roles,application_methods,application_system,coverage,finish,effects,undercoat,medium,volume_ml");
            for (var paint : results) rows.add(java.util.stream.Stream.of(
                    csv(paint.id()), csv(paint.brand()), csv(paint.range()), csv(paint.reference()),
                    csv(paint.name()), csv(paint.colorHex()), csv(paint.colorFamily()),
                    csv(String.join("|", paint.profile().roles())),
                    csv(String.join("|", paint.profile().applicationMethods())),
                    csv(paint.profile().applicationSystem()), csv(paint.profile().coverage()),
                    csv(paint.profile().finish()), csv(String.join("|", paint.profile().effects())),
                    csv(paint.profile().undercoatTone()), csv(paint.profile().medium()), csv(paint.volumeMl()))
                    .collect(java.util.stream.Collectors.joining(",")));
            return String.join("\n", rows) + "\n";
        }
        if ("yaml".equals(format)) {
            var output = new StringBuilder("paints:\n");
            for (var paint : results) {
                output.append("  - id: ").append(quoted(paint.id())).append('\n');
                output.append("    brand: ").append(quoted(paint.brand())).append('\n');
                output.append("    range: ").append(quoted(paint.range())).append('\n');
                output.append("    reference: ").append(quoted(paint.reference())).append('\n');
                output.append("    name: ").append(quoted(paint.name())).append('\n');
            }
            return output.toString();
        }
        throw new com.minipaintdex.domain.shared.DomainException(
                "not_found", "Unknown export format: " + format);
    }

    private static String csv(Object value) {
        return "\"" + String.valueOf(value == null ? "" : value).replace("\"", "\"\"") + "\"";
    }

    private static PaintModelView.Filter filter(
            String id, String parameter, String facet, String labelKey, String vocabulary, int order) {
        var group = switch (id) {
            case "brand", "range" -> "catalog";
            case "role", "applicationMethod", "color" -> "primary";
            default -> "advanced";
        };
        return new PaintModelView.Filter(id, parameter, facet, labelKey, vocabulary, "checkbox", group, order);
    }

    private static PaintModelView.Filter toggle(
            String id, String parameter, String labelKey, int order) {
        return new PaintModelView.Filter(id, parameter, null, labelKey, null, "toggle", "advanced", order);
    }

    private static PaintModelView.SortOption sort(
            String id, String queryValue, String labelKey, int order) {
        return new PaintModelView.SortOption(id, queryValue, labelKey, order);
    }

    private static PaintModelView.Vocabulary vocabulary(String id, List<String> values) {
        return new PaintModelView.Vocabulary(id, values);
    }

    private static String quoted(String value) {
        return "\"" + (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
