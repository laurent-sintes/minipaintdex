package com.minipaintdex.adapter.file;

import java.nio.file.Path;
import java.util.Objects;

public record FileRepositoryLayout(
        Path siteConfiguration,
        Path paintProductCatalogDirectory,
        Path shoppingList,
        Path marketPaintableProductsDirectory,
        Path paintingGuidesDirectory,
        Path ledgerDirectory,
        Path eventPublicationsDirectory,
        Path mediaDirectory) {

    public FileRepositoryLayout {
        siteConfiguration = normalized(siteConfiguration);
        paintProductCatalogDirectory = normalized(paintProductCatalogDirectory);
        shoppingList = normalized(shoppingList);
        marketPaintableProductsDirectory = normalized(marketPaintableProductsDirectory);
        paintingGuidesDirectory = normalized(paintingGuidesDirectory);
        ledgerDirectory = normalized(ledgerDirectory);
        eventPublicationsDirectory = normalized(eventPublicationsDirectory);
        mediaDirectory = normalized(mediaDirectory);
    }

    private static Path normalized(Path path) {
        return Objects.requireNonNull(path).toAbsolutePath().normalize();
    }
}
