package com.minipaintdex.application.usecase;

import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.view.MarketPaintView;
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
     * Reads a bounded, brand-filtered edition page from one immutable generation. Default ordering
     * is by stable ID; only id sorting is supported. Invalid queries fail without mutation. Reads
     * are repeatable for the same generation, need no idempotency key and hold no resource open.
     */
    PageResult<com.minipaintdex.domain.market.paint.PaintCatalogEdition> searchPaintCatalogEditions(
            com.minipaintdex.application.query.SearchPaintCatalogEditionsQuery query);
    /** Returns one edition from a single immutable generation or raises not_found; read-only, no resources retained. */
    com.minipaintdex.domain.market.paint.PaintCatalogEdition getPaintCatalogEdition(
            com.minipaintdex.application.query.GetPaintCatalogEditionQuery query);

    /** Returns all matching paint views in stable name order for CLI-sized reads. */
    List<MarketPaintView> searchMarketPaints(SearchMarketPaintsQuery filters);
    /** Opens a lazy, storage-independent stream; callers must close it after consumption. */
    Stream<MarketPaintView> streamMarketPaints(SearchMarketPaintsQuery filters);
    /** Returns one bounded page and rejects unsupported sort fields or excessive sizes. */
    PageResult<MarketPaintView> searchMarketPaintPage(SearchMarketPaintsQuery filters,
            boolean manufacturerSheetOnly, boolean realResultOnly, PageQuery page);
    /** Counts reference alternatives with other filters retained and the counted facet excluded.
     * Brand and range share one OR group, excluded together; zero-count values remain present.
     * The total applies all filters. Reads are side-effect free and use one catalog snapshot.
     */
    PaintFacetsView marketPaintFacets(SearchMarketPaintsQuery filters,
            boolean manufacturerSheetOnly, boolean realResultOnly);
    /** Publishes the versioned canonical paint model and its supported search facets. */
    PaintModelView marketPaintModel();
    /** Reports canonical completeness and image-provenance quality without reading source envelopes. */
    PaintCatalogQualityView marketPaintQuality();
    /** Returns one market paint or raises a not-found application error. */
    MarketPaintView getMarketPaint(String id);
    /** Lists market products without materializing their complete detail views. */
    List<PaintableProductSummaryView> listMarketPaintableProducts();
    /** Returns one complete paintable-product reference or raises not found. */
    PaintableProductView getMarketPaintableProduct(String productId);
    /** Lists sourced guides, optionally restricted to one catalog item. */
    List<MarketPaintingGuideView> listMarketPaintingGuides(String catalogItemId);
    /** Exports the current paint catalog in the requested supported adapter-neutral format. */
    String exportPaints(String format);
}
