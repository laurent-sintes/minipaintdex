package com.minipaintdex.application.usecase;
import com.minipaintdex.application.query.*;
import com.minipaintdex.application.result.RackCatalogPage;
import com.minipaintdex.domain.market.storage.*;
/**
 * Detached, sourced Market rack/container reads; no Workshop state. Queries use one catalog
 * generation, stable identity order, case-insensitive text filtering and bounded paging.
 * Unsupported sort or invalid page raises invalid_input; absent detail raises not_found.
 * No mutations, idempotency requirements or retained resources; concurrent edits appear on later reads.
 */
public interface MarketRackQueries {
    RackCatalogPage<RackProduct> searchRackProducts(RackCatalogQuery query);
    RackCatalogPage<PaintContainerFormat> searchContainerFormats(RackCatalogQuery query);
    RackProduct getRackProduct(GetRackReferenceQuery query);
    PaintContainerFormat getContainerFormat(GetRackReferenceQuery query);
}
