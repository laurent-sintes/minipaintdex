package com.minipaintdex.server.api;

import com.minipaintdex.application.query.GetPaintCatalogEditionQuery;
import com.minipaintdex.application.query.SearchPaintCatalogEditionsQuery;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SortOrder;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.domain.market.paint.PaintCatalogEdition;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/market/paint-catalog-editions")
final class PaintCatalogEditionController {
    private final MarketCatalogUseCases market;
    PaintCatalogEditionController(MarketCatalogUseCases market) { this.market = market; }

    @GetMapping
    EntityModel<EditionPage> editions(@RequestParam(required = false) String brand,
            @ParameterObject Pageable pageable, @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        var query = new PageQuery(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().stream()
                .map(order -> new SortOrder(order.getProperty(), order.isAscending()
                        ? SortOrder.Direction.ASCENDING : SortOrder.Direction.DESCENDING)).toList());
        var result = market.searchPaintCatalogEditions(new SearchPaintCatalogEditionsQuery(brand, query, correlation(correlationId)));
        var model = EntityModel.of(new EditionPage(result.content().stream().map(PaintCatalogEditionController::model).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()), pageLink(result.page(), result.size()).withSelfRel());
        model.add(pageLink(0, result.size()).withRel("first"));
        if (result.hasPrevious()) model.add(pageLink(result.page() - 1, result.size()).withRel("previous"));
        if (result.hasNext()) model.add(pageLink(result.page() + 1, result.size()).withRel("next"));
        model.add(pageLink(Math.max(0, result.totalPages() - 1), result.size()).withRel("last"));
        return model;
    }

    @GetMapping("/{id}")
    EntityModel<PaintCatalogEdition> edition(@PathVariable String id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return model(market.getPaintCatalogEdition(new GetPaintCatalogEditionQuery(id, correlation(correlationId))));
    }

    private static EntityModel<PaintCatalogEdition> model(PaintCatalogEdition edition) {
        return EntityModel.of(edition, Link.of("/api/v1/market/paint-catalog-editions/" + edition.id()).withSelfRel(),
                Link.of("/api/v1/market/paint-catalog-editions").withRel("collection"));
    }
    private static String correlation(String value) { return value == null || value.isBlank() ? UUID.randomUUID().toString() : value; }
    private static Link pageLink(int page, int size) {
        return Link.of(ServletUriComponentsBuilder.fromCurrentRequest().replaceQueryParam("page", page)
                .replaceQueryParam("size", size).build().toUriString());
    }
    record EditionPage(List<EntityModel<PaintCatalogEdition>> editions, int page, int size, long total, int totalPages) {}
}
