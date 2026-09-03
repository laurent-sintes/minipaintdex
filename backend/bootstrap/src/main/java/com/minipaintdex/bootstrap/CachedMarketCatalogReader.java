package com.minipaintdex.bootstrap;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.validation.MarketCatalogFactory;
import com.minipaintdex.domain.market.product.PaintableProduct;
import java.util.List;

/** Translates immutable file generations once, without making Workshop changes invalidate Market. */
final class CachedMarketCatalogReader implements MarketCatalogReader {
    private final SnapshotRepository snapshots;
    private Sources sources;
    private MarketCatalogSnapshot catalog;

    CachedMarketCatalogReader(SnapshotRepository snapshots) { this.snapshots = snapshots; }

    // Synchronize load and publication together: a slow reader must not overwrite a newer cache.
    // Only Market source lists are retained; no owner events or pending publications enter the cache.
    @Override public synchronized MarketCatalogSnapshot load() {
        var snapshot = snapshots.load();
        var current = new Sources(snapshot.paintProducts(), snapshot.paintableProducts(),
                snapshot.marketPaintingGuides(), snapshot.paintCatalogEditions(), snapshot.paintUsageGuides());
        if (!current.equals(sources)) {
            var replacement = MarketCatalogFactory.create(current.paints(), current.products(), current.guides(), current.editions(), current.usageGuides());
            catalog = replacement;
            sources = current;
        }
        return catalog;
    }

    private record Sources(List<StructuredDocument> paints, List<PaintableProduct> products,
            List<StructuredDocument> guides, List<StructuredDocument> editions, List<StructuredDocument> usageGuides) {}
}
