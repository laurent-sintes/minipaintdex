package com.minipaintdex.bootstrap;

import com.minipaintdex.adapter.file.FilePersistenceAdapters;
import com.minipaintdex.adapter.file.FileEventPublicationStore;
import com.minipaintdex.adapter.file.FileRepositoryLayout;
import com.minipaintdex.adapter.springevents.EventBusSettings;
import com.minipaintdex.adapter.springevents.SpringEventBus;
import com.minipaintdex.application.MiniPaintDexService;
import com.minipaintdex.application.AdministrationApplicationService;
import com.minipaintdex.application.MarketCatalogApplicationService;
import com.minipaintdex.application.SiteApplicationService;
import com.minipaintdex.application.WorkshopApplicationService;
import com.minipaintdex.application.WorkshopMediaPolicy;
import com.minipaintdex.application.port.CommittedEventPublisher;
import com.minipaintdex.application.port.EventBus;
import com.minipaintdex.application.port.EventPublicationStore;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.port.EventLedger;
import com.minipaintdex.application.port.MarketPaintCatalogWriter;
import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.port.PaintableProductCatalogWriter;
import com.minipaintdex.application.port.PersistenceLifecycle;
import com.minipaintdex.application.port.WorkshopMediaStorage;
import com.minipaintdex.application.port.WorkshopPaintInventoryWriter;
import com.minipaintdex.domain.market.paint.PaintMatchEngine;
import com.minipaintdex.domain.market.paint.PaintMatchingPolicy;
import com.minipaintdex.application.usecase.AdministrationUseCases;
import com.minipaintdex.application.usecase.MarketCatalogUseCases;
import com.minipaintdex.application.usecase.SiteQueries;
import com.minipaintdex.application.usecase.WorkshopUseCases;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MiniPaintDexProperties.class)
@EnableScheduling
public class MiniPaintDexSpringConfiguration {
    @Bean
    FileRepositoryLayout fileRepositoryLayout(MiniPaintDexProperties properties) {
        var root = properties.root().toAbsolutePath().normalize();
        var storage = properties.storage();
        return new FileRepositoryLayout(
                resolve(root, storage.siteConfiguration()),
                resolve(root, storage.marketPaintCatalog()),
                resolve(root, storage.workshopPaintInventory()),
                resolve(root, storage.shoppingList()),
                resolve(root, storage.marketPaintableProductsDirectory()),
                resolve(root, storage.paintingGuidesDirectory()),
                resolve(root, storage.ledgerDirectory()),
                resolve(root, storage.eventPublicationsDirectory()),
                resolve(root, storage.mediaDirectory()));
    }

    @Bean(initMethod = "initialize")
    FilePersistenceAdapters filePersistenceAdapters(FileRepositoryLayout layout) {
        return new FilePersistenceAdapters(layout);
    }

    @Bean("committedSnapshots")
    SnapshotRepository committedSnapshots(FilePersistenceAdapters adapters) {
        return adapters.snapshots();
    }

    @Bean
    EventLedger eventLedger(FilePersistenceAdapters adapters) {
        return adapters.ledger();
    }

    @Bean
    MarketPaintCatalogWriter marketPaintCatalogWriter(FilePersistenceAdapters adapters) {
        return adapters.marketPaints();
    }

    @Bean
    WorkshopPaintInventoryWriter workshopPaintInventoryWriter(FilePersistenceAdapters adapters) {
        return adapters.workshopPaints();
    }

    @Bean
    PaintableProductCatalogWriter paintableProductCatalogWriter(FilePersistenceAdapters adapters) {
        return adapters.paintableProducts();
    }

    @Bean
    WorkshopMediaStorage workshopMediaStorage(FilePersistenceAdapters adapters) {
        return adapters.media();
    }

    @Bean
    PersistenceLifecycle persistenceLifecycle(FilePersistenceAdapters adapters) {
        return adapters.lifecycle();
    }

    @Bean
    PersistenceSentinel persistenceSentinel(
            PersistenceLifecycle persistence,
            MiniPaintDexProperties properties) {
        return new PersistenceSentinel(persistence, properties.storage().sentinelEnabled());
    }

    @Bean
    EventPublicationStore eventPublicationStore(FileRepositoryLayout layout) {
        return new FileEventPublicationStore(layout.eventPublicationsDirectory());
    }

    @Bean
    @Primary
    SnapshotRepository effectiveSnapshotRepository(
            @Qualifier("committedSnapshots") SnapshotRepository snapshots,
            EventPublicationStore publications) {
        return new EffectiveSnapshotRepository(snapshots, publications);
    }

    @Bean
    CommittedEventPublisher committedEventPublisher(ApplicationEventPublisher publisher) {
        return publisher::publishEvent;
    }

    @Bean
    EventBus eventBus(
            ApplicationEventPublisher publisher,
            EventPublicationStore publications,
            EventLedger ledger,
            CommittedEventPublisher committedEvents,
            MiniPaintDexProperties properties) {
        var eventing = properties.eventing();
        return new SpringEventBus(
                publisher, publications, ledger, committedEvents,
                new EventBusSettings(
                        eventing.workerCount(), eventing.queueCapacity(), eventing.maxAttempts(),
                        eventing.retryDelay(), eventing.shutdownTimeout()));
    }

    @Bean
    PaintMatchEngine paintMatchEngine(MiniPaintDexProperties properties) {
        var matching = properties.paintMatching();
        var scores = matching.scores();
        return new PaintMatchEngine(new PaintMatchingPolicy(
                matching.candidateLimit(),
                matching.behavioralTypes(),
                matching.colorDistanceFactor(),
                scores.functionalTypeMismatch(),
                scores.metadataMismatch(),
                scores.missingMetadata(),
                scores.emptyBehavior(),
                scores.closeColorThreshold(),
                scores.similarBehaviorThreshold(),
                weights(matching.standard()),
                weights(matching.behavioral())));
    }

    @Bean
    WorkshopMediaPolicy workshopMediaPolicy(MiniPaintDexProperties properties) {
        return new WorkshopMediaPolicy(properties.media().maxUploadBytes(), properties.media().allowedContentTypes());
    }

    @Bean
    MiniPaintDexService miniPaintDexService(
            SnapshotRepository snapshots,
            EventBus eventBus,
            MarketPaintCatalogWriter marketPaints,
            WorkshopPaintInventoryWriter workshopPaints,
            PaintableProductCatalogWriter paintableProducts,
            WorkshopMediaStorage media,
            WorkshopMediaPolicy mediaPolicy,
            PaintMatchEngine paintMatchEngine) {
        return new MiniPaintDexService(
                snapshots, eventBus, marketPaints, workshopPaints, paintableProducts, media,
                mediaPolicy, paintMatchEngine);
    }

    @Bean
    SiteQueries siteQueries(MiniPaintDexService kernel) {
        return new SiteApplicationService(kernel);
    }

    @Bean
    MarketCatalogReader marketCatalogReader(SnapshotRepository snapshots) {
        return () -> {
            var snapshot = snapshots.load();
            return new MarketCatalogSnapshot(
                    snapshot.marketPaints(), snapshot.paintableProducts(), snapshot.marketPaintingGuides());
        };
    }

    @Bean
    MarketCatalogUseCases marketCatalogUseCases(MarketCatalogReader catalogs) {
        return new MarketCatalogApplicationService(catalogs);
    }

    @Bean
    WorkshopUseCases workshopUseCases(
            MiniPaintDexService kernel,
            MarketCatalogUseCases market,
            SnapshotRepository snapshots) {
        return new WorkshopApplicationService(kernel, market, snapshots);
    }

    @Bean
    AdministrationUseCases administrationUseCases(MiniPaintDexService kernel) {
        return new AdministrationApplicationService(kernel);
    }

    private static PaintMatchingPolicy.Weights weights(MiniPaintDexProperties.Weights weights) {
        return new PaintMatchingPolicy.Weights(
                weights.color(), weights.functionalType(), weights.behavior(),
                weights.finish(), weights.opacity(), weights.medium());
    }

    private static Path resolve(Path root, Path configured) {
        return (configured.isAbsolute() ? configured : root.resolve(configured)).normalize();
    }
}
