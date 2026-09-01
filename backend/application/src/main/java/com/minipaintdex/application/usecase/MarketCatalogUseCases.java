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
    /** Returns all matching paint views in stable name order for CLI-sized reads. */
    List<MarketPaintView> searchMarketPaints(SearchMarketPaintsQuery filters);
    /** Opens a lazy, storage-independent stream; callers must close it after consumption. */
    Stream<MarketPaintView> streamMarketPaints(SearchMarketPaintsQuery filters);
    /** Returns one bounded page and rejects unsupported sort fields or excessive sizes. */
    PageResult<MarketPaintView> searchMarketPaintPage(SearchMarketPaintsQuery filters,
            boolean manufacturerSheetOnly, boolean realResultOnly, PageQuery page);
    /** Counts available filter values in the market catalog. */
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
