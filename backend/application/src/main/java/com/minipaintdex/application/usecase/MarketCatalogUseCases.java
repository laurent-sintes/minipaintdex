package com.minipaintdex.application.usecase;

import com.minipaintdex.application.query.SearchPaintProductsQuery;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.view.PaintProductView;
import com.minipaintdex.application.view.PaintFacetsView;
import com.minipaintdex.application.view.PaintCatalogQualityView;
import com.minipaintdex.application.view.PaintCatalogQualityView;
import com.minipaintdex.application.view.PaintModelView;
import com.minipaintdex.application.view.PaintableProductSummaryView;
import com.minipaintdex.application.view.PaintableProductView;
import com.minipaintdex.application.view.MarketPaintingGuideView;

import java.util.List;
import java.util.stream.Stream;

/** Queries the immutable market knowledge base without exposing its storage strategy. */
public interface MarketCatalogUseCases {
    /**
     * Reads a bounded guide page in stable ID order, optionally filtered by brand, range and explicit
     * product links. Only id/title sorting and en/fr languages are supported. Missing products raise
     * not_found; invalid queries raise invalid_input. A current requested translation is preferred;
     * missing/stale translations fall back visibly to the original without upgrading authority.
     * Each concurrent read uses one validated generation, mutates nothing, needs no idempotency key,
     * echoes correlation and returns immutable values retaining no resources.
     */
    com.minipaintdex.application.result.PaintUsageGuidesResult searchPaintUsageGuides(
            com.minipaintdex.application.query.SearchPaintUsageGuidesQuery query);
    /** Same translation, consistency and lifetime guarantees as search; an unknown ID raises not_found. */
    com.minipaintdex.application.result.PaintUsageGuideResult getPaintUsageGuide(
            com.minipaintdex.application.query.GetPaintUsageGuideQuery query);

    /**
     * Searches results, suggestions, or both from one ranked selection. Results are pageable and
     * explicitly sortable; suggestions stay relevance-ordered, bounded and empty for blank text.
     * Workshop applies ownership before limiting; Market never reads Workshop state.
     * Unrequested parts are null. Invalid include/paging/text/limit raises invalid_input;
     * index failures raise search_unavailable, never a misleading empty result.
     * Each source is read once per call as an immutable generation; concurrent changes appear
     * on later reads. Read-only, no idempotency key, correlation echoed, no resources retained.
     */
    com.minipaintdex.application.result.PaintSearchResult<PaintProductView> searchPaintProducts(
            com.minipaintdex.application.query.PaintSearchQuery query);

    /**
     * Reads a bounded, brand-filtered edition page from one immutable generation. Default ordering
     * is by stable ID; only id sorting is supported. Invalid queries fail without mutation. Reads
     * are repeatable for the same generation, need no idempotency key and hold no resource open.
     */
    PageResult<com.minipaintdex.domain.market.paint.PaintCatalogEdition> searchPaintCatalogEditions(
            com.minipaintdex.application.query.SearchPaintCatalogEditionsQuery query);
    /** Returns one edition from a single immutable generation or raises not_found; read-only, no resources retained. */
    com.minipaintdex.domain.market.paint.PaintCatalogEdition getPaintCatalogEdition(
            com.minipaintdex.application.query.GetPaintCatalogEditionQuery query);

    /** Returns matching references in relevance order, then stable name/ID order; blank text sorts by name/ID. */
    List<PaintProductView> searchPaintProducts(SearchPaintProductsQuery filters);
    /** Opens a lazy, storage-independent stream; callers must close it after consumption. */
    Stream<PaintProductView> streamPaintProducts(SearchPaintProductsQuery filters);
    /** Counts reference alternatives with other filters retained and the counted facet excluded.
     * Brand and range share one OR group, excluded together; zero-count values remain present.
     * The total applies all filters. Reads are side-effect free and use one catalog snapshot.
     */
    PaintFacetsView paintProductFacets(SearchPaintProductsQuery filters,
            boolean manufacturerSheetOnly, boolean realResultOnly);
    /** Publishes the versioned canonical paint model and its supported search facets. */
    PaintModelView paintProductModel();
    /** Reports canonical completeness and image-provenance quality without reading source envelopes. */
    PaintCatalogQualityView paintProductQuality();
    /** Returns one market paint or raises a not-found application error. */
    PaintProductView getPaintProduct(String id);
    /** Lists market products without materializing their complete detail views. */
    List<PaintableProductSummaryView> listMarketPaintableProducts();
    /** Returns one complete paintable-product reference or raises not found. */
    PaintableProductView getMarketPaintableProduct(String paintableProductId);
    /** Lists sourced guides, optionally restricted to one paintable component. */
    List<MarketPaintingGuideView> listMarketPaintingGuides(String paintableComponentId);
    /** Exports the current paint catalog in the requested supported adapter-neutral format. */
    String exportPaints(String format);
}
