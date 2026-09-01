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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


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
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String applicationMethod,
            @RequestParam(required = false) String applicationSystem,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String finish,
            @RequestParam(required = false) String medium,
            @RequestParam(required = false) String coverage,
            @RequestParam(required = false) String effect,
            @RequestParam(required = false) String undercoat,
            @RequestParam(required = false) String lifecycle,
            @RequestParam(defaultValue = "false") boolean manufacturerSheetOnly,
            @RequestParam(defaultValue = "false") boolean realResultOnly,
            Pageable pageable) {
        var filters = new SearchMarketPaintsQuery(
                query, brand, range, role, applicationMethod, applicationSystem,
                color, finish, medium, coverage, effect, undercoat, lifecycle);
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
    PaintFacetsView paintFacets(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String applicationMethod,
            @RequestParam(required = false) String applicationSystem,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String finish,
            @RequestParam(required = false) String medium,
            @RequestParam(required = false) String coverage,
            @RequestParam(required = false) String effect,
            @RequestParam(required = false) String undercoat,
            @RequestParam(required = false) String lifecycle) {
        return market.marketPaintFacets(new SearchMarketPaintsQuery(
                query, brand, range, role, applicationMethod, applicationSystem,
                color, finish, medium, coverage, effect, undercoat, lifecycle));
    }

    @GetMapping(value = "/market/paint-model", produces = "application/schema+json")
    ResponseEntity<Map<String, Object>> paintModel() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/schema+json"))
                .body(schema(market.marketPaintModel()));
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

    private static Map<String, Object> schema(com.minipaintdex.application.view.PaintModelView model) {
        var vocabularies = new LinkedHashMap<String, List<String>>();
        model.vocabularies().forEach(vocabulary -> vocabularies.put(vocabulary.id(), vocabulary.values()));
        var filterById = new LinkedHashMap<String, com.minipaintdex.application.view.PaintModelView.Filter>();
        model.filters().forEach(filter -> filterById.put(filter.id(), filter));

        var profileProperties = new LinkedHashMap<String, Object>();
        profileProperties.put("roles", arrayProperty("paint-role", vocabularies, filterById.get("role")));
        profileProperties.put("application_methods", arrayProperty(
                "application-method", vocabularies, filterById.get("applicationMethod")));
        profileProperties.put("application_system", scalarProperty(
                "application-system", vocabularies, filterById.get("applicationSystem")));
        profileProperties.put("coverage", scalarProperty("coverage", vocabularies, filterById.get("coverage")));
        profileProperties.put("finish", scalarProperty("finish", vocabularies, filterById.get("finish")));
        profileProperties.put("effects", arrayProperty("effect", vocabularies, filterById.get("effect")));
        profileProperties.put("undercoat", Map.of(
                "type", "object", "additionalProperties", false,
                "required", List.of("tone", "pre_highlighted_surface_recommended"),
                "properties", Map.of(
                        "tone", scalarProperty("undercoat-tone", vocabularies, filterById.get("undercoat")),
                        "pre_highlighted_surface_recommended", Map.of("type", "boolean"))));
        profileProperties.put("medium", scalarProperty("medium", vocabularies, filterById.get("medium")));

        var properties = new LinkedHashMap<String, Object>();
        properties.put("schema_version", Map.of("type", "integer", "const", model.modelVersion()));
        properties.put("id", Map.of("type", "string", "pattern", "^[a-z0-9]+(?:-[a-z0-9]+)*$"));
        properties.put("brand", stringFilter(filterById.get("brand")));
        properties.put("manufacturer", Map.of("type", "string", "minLength", 1));
        properties.put("range", stringFilter(filterById.get("range")));
        properties.put("reference", Map.of("type", "string"));
        properties.put("name", Map.of("type", "string", "minLength", 1));
        properties.put("profile", Map.of(
                "type", "object", "additionalProperties", false,
                "required", List.of("roles", "application_methods", "application_system", "coverage",
                        "finish", "effects", "undercoat", "medium"),
                "properties", profileProperties));
        properties.put("color", Map.of(
                "type", "object",
                "properties", Map.of("family", stringFilter(filterById.get("color")),
                        "hex", Map.of("type", "string", "pattern", "^#[0-9a-fA-F]{6}$"))));
        properties.put("lifecycle_status", stringFilter(filterById.get("lifecycle")));

        var result = new LinkedHashMap<String, Object>();
        result.put("$schema", model.jsonSchemaDraft());
        result.put("$id", "urn:minipaintdex:schema:market-paint:" + model.modelVersion());
        result.put("title", "Mini Paint Dex canonical market paint");
        result.put("type", "object");
        result.put("additionalProperties", true);
        result.put("required", List.of("schema_version", "id", "brand", "manufacturer", "range", "name", "profile"));
        result.put("properties", properties);
        result.put("x-model-version", model.modelVersion());
        result.put("x-filters", model.filters());
        result.put("x-vocabularies", vocabularies);
        return result;
    }

    private static Map<String, Object> stringFilter(
            com.minipaintdex.application.view.PaintModelView.Filter filter) {
        var result = new LinkedHashMap<String, Object>();
        result.put("type", "string");
        if (filter != null) result.put("x-filter", filter);
        return result;
    }

    private static Map<String, Object> scalarProperty(
            String vocabulary,
            Map<String, List<String>> vocabularies,
            com.minipaintdex.application.view.PaintModelView.Filter filter) {
        var result = new LinkedHashMap<String, Object>();
        result.put("type", "string");
        result.put("enum", vocabularies.get(vocabulary));
        if (filter != null) result.put("x-filter", filter);
        return result;
    }

    private static Map<String, Object> arrayProperty(
            String vocabulary,
            Map<String, List<String>> vocabularies,
            com.minipaintdex.application.view.PaintModelView.Filter filter) {
        var result = new LinkedHashMap<String, Object>();
        result.put("type", "array");
        result.put("uniqueItems", true);
        result.put("items", Map.of("type", "string", "enum", vocabularies.get(vocabulary)));
        if (filter != null) result.put("x-filter", filter);
        return result;
    }
}
