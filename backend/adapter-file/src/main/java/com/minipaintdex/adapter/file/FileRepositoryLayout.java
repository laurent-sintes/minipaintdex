package com.minipaintdex.adapter.file;

import java.nio.file.Path;
import java.util.Objects;

public record FileRepositoryLayout(
        Path siteConfiguration,
        Path marketPaintCatalog,
        Path workshopPaintInventory,
        Path shoppingList,
        Path marketPaintableProductsDirectory,
        Path paintingGuidesDirectory,
        Path ledgerDirectory,
        Path mediaDirectory) {

    public FileRepositoryLayout {
        siteConfiguration = normalized(siteConfiguration);
        marketPaintCatalog = normalized(marketPaintCatalog);
        workshopPaintInventory = normalized(workshopPaintInventory);
        shoppingList = normalized(shoppingList);
        marketPaintableProductsDirectory = normalized(marketPaintableProductsDirectory);
        paintingGuidesDirectory = normalized(paintingGuidesDirectory);
        ledgerDirectory = normalized(ledgerDirectory);
        mediaDirectory = normalized(mediaDirectory);
    }

    private static Path normalized(Path path) {
        return Objects.requireNonNull(path).toAbsolutePath().normalize();
    }
}
