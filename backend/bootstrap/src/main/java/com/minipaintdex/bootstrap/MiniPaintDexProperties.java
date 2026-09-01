package com.minipaintdex.bootstrap;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Validated
@ConfigurationProperties("minipaintdex")
public record MiniPaintDexProperties(
        @NotNull Path root,
        @Valid @NotNull Application application,
        @Valid @NotNull Storage storage,
        @Valid @NotNull Eventing eventing,
        @Valid @NotNull PaintMatching paintMatching,
        @Valid @NotNull Media media,
        @Valid @NotNull Web web) {

    public record Application(@NotEmpty String name, @NotEmpty String author) {}

    public record Storage(
            @NotNull Path siteConfiguration,
            @NotNull Path marketPaintCatalogDirectory,
            @NotNull Path workshopPaintInventory,
            @NotNull Path shoppingList,
            @NotNull Path marketPaintableProductsDirectory,
            @NotNull Path paintingGuidesDirectory,
            @NotNull Path ledgerDirectory,
            @NotNull Path eventPublicationsDirectory,
            @NotNull Path mediaDirectory,
            boolean sentinelEnabled,
            @NotNull Duration sentinelInterval) {
        @AssertTrue(message = "minipaintdex.storage.sentinel-interval must be positive")
        public boolean isSentinelIntervalPositive() {
            return sentinelInterval != null && !sentinelInterval.isNegative() && !sentinelInterval.isZero();
        }
    }

    public record Eventing(
            @Min(1) @Max(1) int workerCount,
            @Min(1) int queueCapacity,
            @Min(1) int maxAttempts,
            @NotNull Duration retryDelay,
            @NotNull Duration shutdownTimeout) {
        @AssertTrue(message = "minipaintdex.eventing.retry-delay must not be negative")
        public boolean isRetryDelayValid() {
            return retryDelay != null && !retryDelay.isNegative();
        }

        @AssertTrue(message = "minipaintdex.eventing.shutdown-timeout must be positive")
        public boolean isShutdownTimeoutPositive() {
            return shutdownTimeout != null && !shutdownTimeout.isNegative() && !shutdownTimeout.isZero();
        }
    }

    public record PaintMatching(
            @Min(1) int candidateLimit,
            @NotEmpty Set<String> behavioralSystems,
            @DecimalMin(value = "0", inclusive = false) double colorDistanceFactor,
            @Valid @NotNull Scores scores,
            @Valid @NotNull Weights standard,
            @Valid @NotNull Weights behavioral) {}

    public record Scores(
            @DecimalMin("0") @DecimalMax("100") double roleMismatch,
            @DecimalMin("0") @DecimalMax("100") double metadataMismatch,
            @DecimalMin("0") @DecimalMax("100") double missingMetadata,
            @DecimalMin("0") @DecimalMax("100") double emptyBehavior,
            @DecimalMin("0") @DecimalMax("100") double closeColorThreshold,
            @DecimalMin("0") @DecimalMax("100") double similarBehaviorThreshold) {}

    public record Weights(
            @DecimalMin("0") double color,
            @DecimalMin("0") double role,
            @DecimalMin("0") double behavior,
            @DecimalMin("0") double finish,
            @DecimalMin("0") double coverage,
            @DecimalMin("0") double medium) {}

    public record Media(@Min(1) long maxUploadBytes, @NotEmpty Set<String> allowedContentTypes) {}

    public record Web(
            @NotEmpty List<String> allowedOrigins,
            @Min(1) int sseReplayCapacity,
            @Min(1) int sseQueueCapacity,
            @NotNull Duration sseConnectionTimeout,
            @NotNull Duration sseHeartbeat) {
        @AssertTrue(message = "minipaintdex.web.sse-connection-timeout must be positive")
        public boolean isSseConnectionTimeoutPositive() {
            return sseConnectionTimeout != null
                    && !sseConnectionTimeout.isNegative() && !sseConnectionTimeout.isZero();
        }

        @AssertTrue(message = "minipaintdex.web.sse-heartbeat must be positive")
        public boolean isSseHeartbeatPositive() {
            return sseHeartbeat != null && !sseHeartbeat.isNegative() && !sseHeartbeat.isZero();
        }
    }
}
