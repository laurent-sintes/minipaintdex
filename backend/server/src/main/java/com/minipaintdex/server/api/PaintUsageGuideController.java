package com.minipaintdex.server.api;

import com.minipaintdex.application.query.*;
import com.minipaintdex.application.view.PaintUsageGuideView;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import org.springframework.data.domain.Pageable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;

@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", useReturnTypeSchema = true)
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid language, paging or filters",
        content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/problem+json",
                schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = org.springframework.http.ProblemDetail.class)))
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown guide or paint product",
        content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "application/problem+json",
                schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = org.springframework.http.ProblemDetail.class)))
@RestController
@RequestMapping(value = "/api/v1/market/paint-usage-guides", produces = {"application/json", "application/hal+json"})
final class PaintUsageGuideController {
    private final MarketCatalogUseCases market;
    PaintUsageGuideController(MarketCatalogUseCases market) { this.market = market; }
    @GetMapping
    @Operation(operationId = "searchPaintUsageGuides", summary = "Read shared paint instructions and revision-bound translations")
    EntityModel<GuidePageResponse> search(@RequestParam(required = false) String brand,
            @RequestParam(required = false) String range, @RequestParam(required = false) String paintProductId,
            @RequestParam(defaultValue = "fr") String language, @ParameterObject Pageable pageable,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        var query = new SearchPaintUsageGuidesQuery(brand, range, paintProductId, language,
                new PageQuery(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().stream()
                        .map(o -> new SortOrder(o.getProperty(), o.isAscending() ? SortOrder.Direction.ASCENDING : SortOrder.Direction.DESCENDING)).toList()),
                correlation(correlationId));
        var result = market.searchPaintUsageGuides(query);
        var page = result.page();
        var response = EntityModel.of(new GuidePageResponse(page.content().stream().map(g -> model(g, language)).toList(),
                page.totalElements(), page.page(), page.size(), page.totalPages(), result.correlationId()),
                pageLink(page.page(), page.size()).withSelfRel(), pageLink(0, page.size()).withRel("first"),
                pageLink(Math.max(0, page.totalPages() - 1), page.size()).withRel("last"));
        if (page.hasPrevious()) response.add(pageLink(page.page() - 1, page.size()).withRel("previous"));
        if (page.hasNext()) response.add(pageLink(page.page() + 1, page.size()).withRel("next"));
        return response;
    }
    @GetMapping("/{paintUsageGuideId}")
    @Operation(operationId = "getPaintUsageGuide", summary = "Read one shared paint usage guide")
    EntityModel<com.minipaintdex.application.result.PaintUsageGuideResult> get(@PathVariable String paintUsageGuideId,
            @RequestParam(defaultValue = "fr") String language,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return EntityModel.of(market.getPaintUsageGuide(new GetPaintUsageGuideQuery(paintUsageGuideId, language, correlation(correlationId))),
                Link.of("/api/v1/market/paint-usage-guides/" + paintUsageGuideId + "?language=" + language).withSelfRel(),
                Link.of("/api/v1/market/paint-usage-guides").withRel("collection"));
    }
    private static EntityModel<PaintUsageGuideView> model(PaintUsageGuideView guide, String language) {
        return EntityModel.of(guide, Link.of("/api/v1/market/paint-usage-guides/" + guide.paintUsageGuideId() + "?language=" + language).withSelfRel());
    }
    private static Link pageLink(int page, int size) {
        return Link.of(ServletUriComponentsBuilder.fromCurrentRequest().replaceQueryParam("page", page).replaceQueryParam("size", size).build().toUriString());
    }
    private static String correlation(String value) { return value == null || value.isBlank() ? UUID.randomUUID().toString() : value; }
    record GuidePageResponse(List<EntityModel<PaintUsageGuideView>> guides, long total, int page, int size, int totalPages, String correlationId) {}
}
