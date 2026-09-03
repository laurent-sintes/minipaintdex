package com.minipaintdex.server.api;

import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SearchPaintProductsQuery;
import com.minipaintdex.application.query.SortOrder;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.view.PaintProductView;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.application.view.PaintCatalogQualityView;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
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

    @org.springframework.web.bind.annotation.PostMapping(value = "/market/paint-products/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = {MediaType.APPLICATION_JSON_VALUE, "application/hal+json"})
    @io.swagger.v3.oas.annotations.Operation(operationId = "searchPaintProducts", summary = "Search results and/or suggestions",
            description = "Read-only MiniPaintDex contract, not Elasticsearch DSL. Body selects results, suggestions, or both. Pagination uses page/size/sort; replay the same body when following POST page links. Suggestions stay relevance-ordered and empty for blank query. Unrequested parts are null.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search response", useReturnTypeSchema = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Malformed or unknown JSON fields",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/problem+json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = org.springframework.http.ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid selection, text, filters, sorting or limit",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/problem+json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = org.springframework.http.ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Search unavailable",
            content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/problem+json", schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = org.springframework.http.ProblemDetail.class)))
    EntityModel<PaintSearchResponse<com.minipaintdex.application.view.PaintProductView>> paintSearch(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody PaintSearchRequest request,
            @ParameterObject Pageable pageable,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return PaintSearchResponse.from(market.searchPaintProducts(request.toQuery(pageable, correlationId)), false);
    }

    @GetMapping("/market/paint-products/facets")
    PaintFacetsView paintFacets(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<String> brand,
            @io.swagger.v3.oas.annotations.Parameter(description = "Repeatable brand::range selection; OR with brands. Escape literal colons and backslashes with a backslash.") @RequestParam(required = false) List<String> range,
            @RequestParam(required = false) List<String> role,
            @RequestParam(required = false) List<String> applicationMethod,
            @RequestParam(required = false) List<String> applicationSystem,
            @RequestParam(required = false) List<String> color,
            @RequestParam(required = false) List<String> finish,
            @RequestParam(required = false) List<String> medium,
            @RequestParam(required = false) List<String> coverage,
            @RequestParam(required = false) List<String> effect,
            @RequestParam(required = false) List<String> undercoat,
            @RequestParam(required = false) List<String> lifecycle,
            @RequestParam(defaultValue = "false") boolean manufacturerSheetOnly,
            @RequestParam(defaultValue = "false") boolean realResultOnly) {
        return market.paintProductFacets(SearchPaintProductsQuery.fromSelections(
                query, brand, range, role, applicationMethod, applicationSystem,
                color, finish, medium, coverage, effect, undercoat, lifecycle),
                manufacturerSheetOnly, realResultOnly);
    }

    @GetMapping(value = "/market/paint-product-model", produces = "application/schema+json")
    ResponseEntity<PaintModelSchemaResponse> paintModel() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/schema+json"))
                .body(schema(market.paintProductModel()));
    }

    @GetMapping("/market/paint-products/quality")
    PaintCatalogQualityView paintQuality() {
        return market.paintProductQuality();
    }

    @GetMapping("/market/paint-products/{paintProductId}")
    EntityModel<PaintProductView> paint(@PathVariable String paintProductId) {
        return EntityModel.of(market.getPaintProduct(paintProductId),
                Link.of("/api/v1/market/paint-products/" + paintProductId).withSelfRel(),
                PaintSearchResponse.postLink("/api/v1/market/paint-products/search").withRel("search"),
                Link.of("/api/v1/market/paint-usage-guides?paintProductId=" + paintProductId).withRel("usage-guides"));
    }

    @GetMapping("/market/paintable-products")
    PaintableProductsResponse paintableProducts() {
        return new PaintableProductsResponse(market.listMarketPaintableProducts());
    }

    @GetMapping("/market/paintable-products/{paintableProductId}")
    EntityModel<PaintableProductResponse> paintableProduct(@PathVariable String paintableProductId) {
        var product = market.getMarketPaintableProduct(paintableProductId);
        var model = EntityModel.of(new PaintableProductResponse(product),
                Link.of("/api/v1/market/paintable-products/" + paintableProductId).withSelfRel(),
                Link.of("/api/v1/market/paintable-products").withRel("collection"),
                Link.of("/api/v1/workshop/painting-project-import-previews/" + paintableProductId)
                        .withRel("workshop-import-preview"),
                Link.of("/api/v1/market/painting-guides").withRel("painting-guides"));
        model.add(Link.of("/api/v1/workshop/painting-projects").withRel("create-painting-project"));
        return model;
    }

    @GetMapping("/market/painting-guides")
    PaintingGuidesResponse paintingGuides(@RequestParam(required = false) String paintableComponentId) {
        return new PaintingGuidesResponse(market.listMarketPaintingGuides(paintableComponentId));
    }

    @GetMapping("/exports/{format}")
    ResponseEntity<String> export(@PathVariable String format) {
        var mediaType = "csv".equals(format) ? "text/csv" : "application/yaml";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mediaType + "; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"paints." + format + "\"")
                .body(market.exportPaints(format));
    }

    private static PaintModelSchemaResponse schema(com.minipaintdex.application.view.PaintModelView model) {
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
        properties.put("id", Map.of(
                "type", "string",
                "pattern", "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                "description", "Stable brand-code and normalized manufacturer-reference identity."));
        properties.put("brand", stringFilter(filterById.get("brand")));
        properties.put("manufacturer", Map.of("type", "string", "minLength", 1));
        properties.put("brand_aliases", stringArrayProperty());
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
                "additionalProperties", false,
                "properties", Map.of("family", stringFilter(filterById.get("color")),
                        "hex", Map.of("type", "string", "pattern", "^#[0-9a-fA-F]{6}$"))));
        properties.put("lifecycle_status", stringFilter(filterById.get("lifecycle")));
        properties.put("data_status", Map.of("type", "string", "minLength", 1));
        properties.put("warnings", stringArrayProperty());
        properties.put("tags", stringArrayProperty());
        properties.put("notes", Map.of("type", "string"));
        properties.put("manufacturer_page", uriProperty());
        properties.put("manufacturer_image", imageProperty(vocabularies, true));
        properties.put("volume_ml", Map.of("type", "integer", "minimum", 0));
        properties.put("recommended_uses", stringArrayProperty());
        properties.put("usage_instructions", usageInstructionsProperty());
        properties.put("usage_guide_ids", stringArrayProperty());
        properties.put("verified_at", Map.of("type", "string", "format", "date"));
        properties.put("result_image", imageProperty(vocabularies, false));
        properties.put("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1));
        properties.put("deduplication_key", Map.of("type", "string"));
        properties.put("provenance", sourceEvidenceProperty());
        properties.put("mapping_report", mappingReportProperty());
        properties.put("source_observation", sourceObservationProperty());
        properties.put("source_snapshots", sourceSnapshotsProperty());
        properties.put("catalog_memberships", Map.of("type", "array", "items", Map.of(
                "type", "object", "additionalProperties", false,
                "required", List.of("catalog_edition_id", "source_url", "locator"),
                "properties", Map.of("catalog_edition_id", Map.of("type", "string", "pattern", "^[a-z0-9]+(?:-[a-z0-9]+)*$"),
                        "source_url", uriProperty(), "locator", Map.of("type", "string", "minLength", 1)))));
        properties.put("observed_brand", Map.of("type", "string"));
        properties.put("observed_range", Map.of("type", "string"));

        return new PaintModelSchemaResponse(
                model.jsonSchemaDraft(),
                "urn:minipaintdex:schema:market-paint:" + model.modelVersion(),
                "MiniPaintDex canonical market paint",
                "object",
                false,
                List.of("schema_version", "id", "brand", "manufacturer", "range", "name", "profile",
                        "data_status", "manufacturer_image"),
                properties,
                model.modelVersion(),
                model.filters(),
                model.sortOptions(),
                vocabularies,
                Map.of(
                        "official_photo", 1, "retailer_photo", 2, "owned_photo", 3,
                        "generic_visual", 4, "color_swatch", 5, "none", 6));
    }

    private static Map<String, Object> imageProperty(
            Map<String, List<String>> vocabularies, boolean manufacturerVisual) {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", Map.of("type", "string"));
        properties.put("source_url", uriProperty());
        properties.put("credit", Map.of("type", "string"));
        properties.put("license", Map.of("type", "string"));
        properties.put("reference_url", uriProperty());
        properties.put("image_quality", Map.of(
                "type", "string", "enum", vocabularies.get("image-quality"),
                "description", "Source quality; lower x-quality-rank is better."));
        properties.put("quality_verified_at", Map.of("type", "string", "format", "date"));
        properties.put("quality_limitation", Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("code", "detail", "observed_at"),
                "properties", Map.of(
                        "code", Map.of("type", "string", "enum", vocabularies.get("image-quality-limitation")),
                        "detail", Map.of("type", "string", "minLength", 1),
                        "observed_at", Map.of("type", "string", "format", "date"))));
        var result = new LinkedHashMap<String, Object>();
        result.put("type", "object");
        result.put("additionalProperties", false);
        result.put("properties", properties);
        if (manufacturerVisual) {
            result.put("required", List.of("image_quality"));
            result.put("allOf", List.of(
                    Map.of(
                            "if", Map.of(
                                    "required", List.of("image_quality"),
                                    "properties", Map.of("image_quality", Map.of("const", "official_photo"))),
                            "then", Map.of("not", Map.of("required", List.of("quality_limitation")))),
                    Map.of(
                            "if", Map.of(
                                    "required", List.of("image_quality"),
                                    "properties", Map.of("image_quality", Map.of(
                                            "enum", List.of("retailer_photo", "owned_photo", "generic_visual", "color_swatch", "none")))),
                            "then", Map.of("required", List.of("quality_limitation"))),
                    Map.of(
                            "if", Map.of("properties", Map.of("image_quality", Map.of(
                                    "enum", List.of("official_photo", "retailer_photo", "owned_photo", "generic_visual", "color_swatch")))),
                            "then", Map.of("required", List.of("quality_verified_at")))));
        }
        return result;
    }

    private static Map<String, Object> usageInstructionsProperty() {
        return Map.of(
                "type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "summary", Map.of("type", "string"),
                        "steps", stringArrayProperty(),
                        "tips", stringArrayProperty(),
                        "instruction_status", Map.of("type", "string"),
                        "review_required", Map.of("type", "boolean")));
    }

    private static Map<String, Object> mappingReportProperty() {
        return Map.of(
                "type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "mapping", Map.of("type", "string"),
                        "mapping_version", Map.of("type", "integer", "const", 1),
                        "mapped_fields", stringArrayProperty(),
                        "unmapped_fields", stringArrayProperty(),
                        "ignored_fields", stringArrayProperty()));
    }

    private static Map<String, Object> sourceObservationProperty() {
        return Map.of(
                "type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "adapter", Map.of("type", "string"),
                        "fields", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object", "additionalProperties", false,
                                        "required", List.of("name", "value"),
                                        "properties", Map.of(
                                                "name", Map.of("type", "string"),
                                                "value", Map.of())))));
    }

    private static Map<String, Object> sourceSnapshotsProperty() {
        return Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "object", "additionalProperties", false,
                        "required", List.of("provider", "url", "payload"),
                        "properties", Map.of(
                                "provider", Map.of("type", "string", "minLength", 1),
                                "url", uriProperty(),
                                "payload", Map.of())));
    }

    private static Map<String, Object> sourceEvidenceProperty() {
        return Map.of(
                "type", "object",
                "description", "Extensible source evidence excluded from canonical search.",
                "additionalProperties", true);
    }

    private static Map<String, Object> stringArrayProperty() {
        return Map.of("type", "array", "uniqueItems", true, "items", Map.of("type", "string"));
    }

    private static Map<String, Object> uriProperty() {
        return Map.of("type", "string", "format", "uri");
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
