package com.minipaintdex.bootstrap;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Validated
@ConfigurationProperties("minipaintdex")
public record MiniPaintDexProperties(
        @NotNull Path root,
        @Valid @NotNull Storage storage,
        @Valid @NotNull PaintMatching paintMatching,
        @Valid @NotNull Web web) {

    public record Storage(
            @NotNull Path siteConfiguration,
            @NotNull Path marketPaintCatalog,
            @NotNull Path workshopPaintInventory,
            @NotNull Path shoppingList,
            @NotNull Path marketPaintableProductsDirectory,
            @NotNull Path paintingGuidesDirectory,
            @NotNull Path ledgerDirectory,
            @NotNull Path mediaDirectory) {}

    public record PaintMatching(
            @Min(1) int candidateLimit,
            @NotEmpty Set<String> behavioralTypes,
            @DecimalMin(value = "0", inclusive = false) double colorDistanceFactor,
            @Valid @NotNull Scores scores,
            @Valid @NotNull Weights standard,
            @Valid @NotNull Weights behavioral) {}

    public record Scores(
            @DecimalMin("0") @DecimalMax("100") double functionalTypeMismatch,
            @DecimalMin("0") @DecimalMax("100") double metadataMismatch,
            @DecimalMin("0") @DecimalMax("100") double missingMetadata,
            @DecimalMin("0") @DecimalMax("100") double emptyBehavior,
            @DecimalMin("0") @DecimalMax("100") double closeColorThreshold,
            @DecimalMin("0") @DecimalMax("100") double similarBehaviorThreshold) {}

    public record Weights(
            @DecimalMin("0") double color,
            @DecimalMin("0") double functionalType,
            @DecimalMin("0") double behavior,
            @DecimalMin("0") double finish,
            @DecimalMin("0") double opacity,
            @DecimalMin("0") double medium) {}

    public record Web(@NotEmpty List<String> allowedOrigins) {}
}
