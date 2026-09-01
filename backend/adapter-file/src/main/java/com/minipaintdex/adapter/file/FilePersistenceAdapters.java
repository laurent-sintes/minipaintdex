package com.minipaintdex.adapter.file;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.port.EventLedger;
import com.minipaintdex.application.port.MarketPaintCatalogWriter;
import com.minipaintdex.application.port.PaintableProductCatalogWriter;
import com.minipaintdex.application.port.PersistenceLifecycle;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.WorkshopMediaStorage;
import com.minipaintdex.application.port.WorkshopPaintInventoryWriter;
import com.minipaintdex.domain.event.EventEnvelope;

import java.util.List;

/**
 * Creates cohesive outbound adapters over the shared atomic file-storage engine.
 * The engine remains shared so catalog batches, cache generations and cross-process locking stay atomic.
 */
public final class FilePersistenceAdapters {
    private final FileMiniPaintDexRepository engine;

    public FilePersistenceAdapters(FileRepositoryLayout layout) {
        engine = new FileMiniPaintDexRepository(layout);
    }

    public void initialize() {
        engine.initialize();
    }

    public SnapshotRepository snapshots() {
        return engine::load;
    }

    public EventLedger ledger() {
        return new EventLedger() {
            @Override
            public List<EventEnvelope> appendAll(List<EventEnvelope> events) {
                return engine.appendAll(events);
            }
        };
    }

    public MarketPaintCatalogWriter marketPaints() {
        return new MarketPaintCatalogWriter() {
            @Override
            public void replaceMarketPaints(List<StructuredDocument> paints) {
                engine.replaceMarketPaints(paints);
            }

            @Override
            public void replaceMarketPaintsAndWorkshopInventory(
                    List<StructuredDocument> paints,
                    List<StructuredDocument> inventory,
                    WorkshopPaintInventoryWriter inventoryWriter) {
                engine.replaceMarketPaintsAndWorkshopInventory(paints, inventory, inventoryWriter);
            }

            @Override
            public void replaceMarketPaintIdentities(
                    List<StructuredDocument> paints,
                    List<StructuredDocument> inventory,
                    List<StructuredDocument> paintingGuides,
                    List<StructuredDocument> shopping) {
                engine.replaceMarketPaintIdentities(paints, inventory, paintingGuides, shopping);
            }
        };
    }

    public WorkshopPaintInventoryWriter workshopPaints() {
        return engine::replaceWorkshopPaints;
    }

    public PaintableProductCatalogWriter paintableProducts() {
        return engine::replaceProduct;
    }

    public WorkshopMediaStorage media() {
        return new WorkshopMediaStorage() {
            @Override
            public StoredMedia store(
                    String itemId, String mediaId, String originalFilename,
                    String contentType, byte[] content) {
                return engine.store(itemId, mediaId, originalFilename, contentType, content);
            }

            @Override
            public void delete(StoredMedia media) {
                engine.delete(media);
            }
        };
    }

    public PersistenceLifecycle lifecycle() {
        return new PersistenceLifecycle() {
            @Override public InitializationReport initialize() { return engine.initialize(); }
            @Override public RefreshResult refreshIfChanged() { return engine.refreshIfChanged(); }
            @Override public PersistenceStatus status() { return engine.status(); }
        };
    }
}
