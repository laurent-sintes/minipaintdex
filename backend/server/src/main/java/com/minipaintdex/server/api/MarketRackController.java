package com.minipaintdex.server.api;
import com.minipaintdex.application.usecase.*;
import com.minipaintdex.application.query.*;
import com.minipaintdex.application.command.SaveRackReferenceCommand;
import com.minipaintdex.application.result.RackCatalogPage;
import com.minipaintdex.domain.market.storage.*;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/market")
final class MarketRackController {
    private final MarketCatalogUseCases market;
    private final AdministrationUseCases administration;
    MarketRackController(MarketCatalogUseCases market, AdministrationUseCases administration) { this.market = market; this.administration = administration; }
    @GetMapping("/rack-products")
    EntityModel<RackCatalogPage<RackProduct>> searchRackProducts(@ParameterObject Pageable pageable, @RequestParam(required=false) String query,
            @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        var result = market.searchRackProducts(new RackCatalogQuery(RackApiSupport.page(pageable), query, RackApiSupport.correlation(correlation)));
        return RackApiSupport.pages(result, result.results());
    }
    @GetMapping("/container-formats")
    EntityModel<RackCatalogPage<PaintContainerFormat>> searchContainerFormats(@ParameterObject Pageable pageable, @RequestParam(required=false) String query,
            @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        var result = market.searchContainerFormats(new RackCatalogQuery(RackApiSupport.page(pageable), query, RackApiSupport.correlation(correlation)));
        return RackApiSupport.pages(result, result.results());
    }
    @GetMapping("/rack-products/{id}")
    EntityModel<RackProduct> getRackProduct(@PathVariable String id, @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        return EntityModel.of(market.getRackProduct(new GetRackReferenceQuery(id, RackApiSupport.correlation(correlation))),
                Link.of("/api/v1/market/rack-products/" + id).withSelfRel(), Link.of("/api/v1/market/rack-products").withRel("collection"));
    }
    @GetMapping("/container-formats/{id}")
    EntityModel<PaintContainerFormat> getContainerFormat(@PathVariable String id, @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        return EntityModel.of(market.getContainerFormat(new GetRackReferenceQuery(id, RackApiSupport.correlation(correlation))),
                Link.of("/api/v1/market/container-formats/" + id).withSelfRel(), Link.of("/api/v1/market/container-formats").withRel("collection"));
    }
    @PostMapping("/rack-catalog/entries")
    CatalogSaved saveRackReference(@RequestBody ReferenceRequest request, @RequestHeader(value="X-Correlation-Id", required=false) String correlation) {
        var id = RackApiSupport.correlation(correlation);
        return new CatalogSaved(administration.saveRackReference(new SaveRackReferenceCommand(request.containerFormat(), request.rackProduct(), request.expectedRevision(), id, request.dryRun())), id);
    }
    record ReferenceRequest(PaintContainerFormat containerFormat, RackProduct rackProduct, long expectedRevision, boolean dryRun) {}
    record CatalogSaved(long catalogRevision, String correlationId) {}
}
