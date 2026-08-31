package com.minipaintdex.server.api;

import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.query.SortOrder;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.PaintFacetsView;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


@RestController
@RequestMapping("/api/v1")
final class MarketCatalogController {
    private final MarketCatalogUseCases market;

    MarketCatalogController(MarketCatalogUseCases market) {
        this.market = market;
    }

    @GetMapping("/market/paints")
    EntityModel<PaintPageResponse> paints(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String finish,
            @RequestParam(required = false) String medium,
            @RequestParam(required = false) String opacity,
            @RequestParam(required = false) String volume,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) String lifecycle,
            @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "false") boolean manufacturerSheetOnly,
            @RequestParam(defaultValue = "false") boolean realResultOnly,
            Pageable pageable) {
        var filters = new SearchMarketPaintsQuery(
                query, brand, range, type, color, finish, medium, opacity, volume,
                reference, lifecycle, manufacturer, tag);
        var pageQuery = new PageQuery(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().stream()
                .map(order -> new SortOrder(order.getProperty(), order.isAscending()
                        ? SortOrder.Direction.ASCENDING : SortOrder.Direction.DESCENDING))
                .toList());
        var result = market.searchMarketPaintPage(
                filters, manufacturerSheetOnly, realResultOnly, pageQuery);
        var response = new PaintPageResponse(
                result.content(), result.totalElements(), result.page(), result.size(), result.totalPages());
        var model = EntityModel.of(response, pageLink(result.page(), result.size()).withSelfRel());
        if (result.hasPrevious()) model.add(pageLink(result.page() - 1, result.size()).withRel("prev"));
        if (result.hasNext()) model.add(pageLink(result.page() + 1, result.size()).withRel("next"));
        model.add(Link.of("/api/v1/market/paints/stream").withRel("stream"));
        return model;
    }

    @GetMapping("/market/paints/facets")
    PaintFacetsView paintFacets() {
        return market.marketPaintFacets();
    }

    @GetMapping("/market/paints/{paintId}")
    EntityModel<MarketPaintView> paint(@PathVariable String paintId) {
        return EntityModel.of(market.getMarketPaint(paintId),
                Link.of("/api/v1/market/paints/" + paintId).withSelfRel(),
                Link.of("/api/v1/market/paints").withRel("collection"));
    }

    @GetMapping("/market/paintable-products")
    PaintableProductsResponse paintableProducts() {
        return new PaintableProductsResponse(market.listMarketPaintableProducts());
    }

    @GetMapping("/market/paintable-products/{productId}")
    EntityModel<PaintableProductResponse> paintableProduct(@PathVariable String productId) {
        var product = market.getMarketPaintableProduct(productId);
        var model = EntityModel.of(new PaintableProductResponse(product),
                Link.of("/api/v1/market/paintable-products/" + productId).withSelfRel(),
                Link.of("/api/v1/market/paintable-products").withRel("collection"),
                Link.of("/api/v1/workshop/painting-project-import-previews/" + productId)
                        .withRel("workshop-import-preview"),
                Link.of("/api/v1/market/painting-guides").withRel("painting-guides"));
        model.add(Link.of("/api/v1/workshop/painting-projects").withRel("create-painting-project"));
        return model;
    }

    @GetMapping("/market/painting-guides")
    PaintingGuidesResponse paintingGuides(@RequestParam(required = false) String catalogItemId) {
        return new PaintingGuidesResponse(market.listMarketPaintingGuides(catalogItemId));
    }

    @GetMapping("/exports/{format}")
    ResponseEntity<String> export(@PathVariable String format) {
        var mediaType = "csv".equals(format) ? "text/csv" : "application/yaml";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mediaType + "; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"paints." + format + "\"")
                .body(market.exportPaints(format));
    }

    private static Link pageLink(int page, int size) {
        var uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page)
                .replaceQueryParam("size", size)
                .build(true).toUriString();
        return Link.of(uri);
    }
}
