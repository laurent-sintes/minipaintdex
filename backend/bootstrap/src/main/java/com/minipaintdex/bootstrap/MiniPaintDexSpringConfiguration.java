package com.minipaintdex.bootstrap;

import com.minipaintdex.adapter.file.FileMiniPaintDexRepository;
import com.minipaintdex.adapter.file.FileRepositoryLayout;
import com.minipaintdex.application.MiniPaintDexService;
import com.minipaintdex.domain.paint.PaintMatchEngine;
import com.minipaintdex.domain.paint.PaintMatchingPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MiniPaintDexProperties.class)
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
                resolve(root, storage.mediaDirectory()));
    }

    @Bean
    FileMiniPaintDexRepository fileMiniPaintDexRepository(FileRepositoryLayout layout) {
        return new FileMiniPaintDexRepository(layout);
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
    MiniPaintDexService miniPaintDexService(FileMiniPaintDexRepository repository, PaintMatchEngine paintMatchEngine) {
        return new MiniPaintDexService(
                repository, repository, repository, repository, repository, paintMatchEngine);
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
