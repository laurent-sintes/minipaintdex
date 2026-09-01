package com.minipaintdex.adapter.file;

import java.nio.file.Path;
import java.util.Objects;

public record FileRepositoryLayout(
        Path siteConfiguration,
        Path marketPaintCatalogDirectory,
        Path workshopPaintInventory,
        Path shoppingList,
        Path marketPaintableProductsDirectory,
        Path paintingGuidesDirectory,
        Path ledgerDirectory,
        Path eventPublicationsDirectory,
        Path mediaDirectory) {

    public FileRepositoryLayout {
        siteConfiguration = normalized(siteConfiguration);
        marketPaintCatalogDirectory = normalized(marketPaintCatalogDirectory);
        workshopPaintInventory = normalized(workshopPaintInventory);
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
