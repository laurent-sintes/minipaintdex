package com.minipaintdex.application;

import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.MarketPaintingGuideView;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.application.view.PaintCatalogQualityView;
import com.minipaintdex.application.view.PaintModelView;
import com.minipaintdex.application.view.PaintableProductSummaryView;
import com.minipaintdex.application.view.PaintableProductView;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import com.minipaintdex.domain.market.paint.MarketPaintLifecycle;
import com.minipaintdex.domain.market.paint.MarketPaintImageQuality;
import com.minipaintdex.domain.market.paint.MarketPaintImageLimitationCode;
import com.minipaintdex.domain.market.paint.MarketPaintProfile;

/** Cohesive read service for the market knowledge bounded context. */
public final class MarketCatalogApplicationService implements MarketCatalogUseCases {
    private final MarketPaintQueryService paints;
    private final MarketProductQueryService products;
    private final MarketCatalogReader catalogs;

    public MarketCatalogApplicationService(MarketCatalogReader catalogs) {
        var reader = Objects.requireNonNull(catalogs);
        this.catalogs = reader;
        this.paints = new MarketPaintQueryService(reader);
        this.products = new MarketProductQueryService(reader, paints);
    }

    @Override public List<MarketPaintView> searchMarketPaints(SearchMarketPaintsQuery query) {
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
    @Override public Stream<MarketPaintView> streamMarketPaints(SearchMarketPaintsQuery query) {
        return paints.stream(query);
    }
    @Override public PageResult<MarketPaintView> searchMarketPaintPage(
            SearchMarketPaintsQuery query, boolean manufacturerSheetOnly,
            boolean realResultOnly, PageQuery page) {
        return paints.page(query, manufacturerSheetOnly, realResultOnly, page);
    }
    @Override public PaintFacetsView marketPaintFacets(
            SearchMarketPaintsQuery filters, boolean manufacturerSheetOnly, boolean realResultOnly) {
        return paints.facets(filters, manufacturerSheetOnly, realResultOnly);
    }
    @Override public PaintModelView marketPaintModel() {
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
                        vocabulary("paint-role", java.util.Arrays.stream(MarketPaintProfile.Role.values()).map(MarketPaintProfile.Role::id).toList()),
                        vocabulary("application-method", java.util.Arrays.stream(MarketPaintProfile.ApplicationMethod.values()).map(MarketPaintProfile.ApplicationMethod::id).toList()),
                        vocabulary("application-system", java.util.Arrays.stream(MarketPaintProfile.ApplicationSystem.values()).map(MarketPaintProfile.ApplicationSystem::id).toList()),
                        vocabulary("coverage", java.util.Arrays.stream(MarketPaintProfile.Coverage.values()).map(MarketPaintProfile.Coverage::id).toList()),
                        vocabulary("finish", java.util.Arrays.stream(MarketPaintProfile.Finish.values()).map(MarketPaintProfile.Finish::id).toList()),
                        vocabulary("effect", java.util.Arrays.stream(MarketPaintProfile.Effect.values()).map(MarketPaintProfile.Effect::id).toList()),
                        vocabulary("undercoat-tone", java.util.Arrays.stream(MarketPaintProfile.UndercoatTone.values()).map(MarketPaintProfile.UndercoatTone::id).toList()),
                        vocabulary("medium", java.util.Arrays.stream(MarketPaintProfile.Medium.values()).map(MarketPaintProfile.Medium::id).toList()),
                        vocabulary("lifecycle", java.util.Arrays.stream(MarketPaintLifecycle.values()).map(MarketPaintLifecycle::id).toList()),
                        vocabulary("image-quality", java.util.Arrays.stream(MarketPaintImageQuality.values()).map(MarketPaintImageQuality::id).toList()),
                        vocabulary("image-quality-limitation", java.util.Arrays.stream(MarketPaintImageLimitationCode.values())
                                .map(MarketPaintImageLimitationCode::id).toList())));
    }
    @Override public PaintCatalogQualityView marketPaintQuality() {
        return paints.quality();
    }
    @Override public MarketPaintView getMarketPaint(String id) {
        return paints.search(SearchMarketPaintsQuery.empty()).stream()
                .filter(paint -> id.equals(paint.id())).findFirst()
                .orElseThrow(() -> new com.minipaintdex.domain.shared.DomainException(
                        "not_found", "Paint not found: " + id));
    }
    @Override public List<PaintableProductSummaryView> listMarketPaintableProducts() {
        return products.summaries();
    }
    @Override public PaintableProductView getMarketPaintableProduct(String productId) {
        return products.product(productId);
    }
    @Override public List<MarketPaintingGuideView> listMarketPaintingGuides(String catalogItemId) {
        return products.guides(catalogItemId);
    }
    @Override public String exportPaints(String format) {
        var results = paints.search(SearchMarketPaintsQuery.empty());
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
