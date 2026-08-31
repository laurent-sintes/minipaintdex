package com.minipaintdex.application;

import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.MarketPaintingGuideView;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.application.view.PaintableProductSummaryView;
import com.minipaintdex.application.view.PaintableProductView;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Cohesive read service for the market knowledge bounded context. */
public final class MarketCatalogApplicationService implements MarketCatalogUseCases {
    private final MarketPaintQueryService paints;
    private final MarketProductQueryService products;

    public MarketCatalogApplicationService(MarketCatalogReader catalogs) {
        var reader = Objects.requireNonNull(catalogs);
        this.paints = new MarketPaintQueryService(reader);
        this.products = new MarketProductQueryService(reader, paints);
    }

    @Override public List<MarketPaintView> searchMarketPaints(SearchMarketPaintsQuery query) {
        return paints.search(query);
    }
    @Override public Stream<MarketPaintView> streamMarketPaints(SearchMarketPaintsQuery query) {
        return paints.stream(query);
    }
    @Override public PageResult<MarketPaintView> searchMarketPaintPage(
            SearchMarketPaintsQuery query, boolean manufacturerSheetOnly,
            boolean realResultOnly, PageQuery page) {
        return paints.page(query, manufacturerSheetOnly, realResultOnly, page);
    }
    @Override public PaintFacetsView marketPaintFacets() {
        return paints.facets();
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
            rows.add("id,brand,range,reference,name,color_hex,color_family,finish,medium,volume_ml");
            for (var paint : results) rows.add(java.util.stream.Stream.of(
                    csv(paint.id()), csv(paint.brand()), csv(paint.range()), csv(paint.reference()),
                    csv(paint.name()), csv(paint.colorHex()), csv(paint.colorFamily()), csv(paint.finish()),
                    csv(paint.medium()), csv(paint.volumeMl())).collect(java.util.stream.Collectors.joining(",")));
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

    private static String quoted(String value) {
        return "\"" + (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
