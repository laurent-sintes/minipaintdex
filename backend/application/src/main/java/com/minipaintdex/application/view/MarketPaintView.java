package com.minipaintdex.application.view;

import java.util.List;

/** Immutable read model exposed by the market-paint application port. */
public record MarketPaintView(
        String id,
        String brand,
        String manufacturer,
        List<String> brandAliases,
        String range,
        Profile profile,
        String reference,
        String name,
        String colorHex,
        String lifecycleStatus,
        String status,
        String warnings,
        List<String> tags,
        String notes,
        String createdAt,
        String updatedAt,
        String manufacturerUrl,
        String manufacturerImage,
        String manufacturerImageSource,
        String manufacturerImageCredit,
        String manufacturerImageQuality,
        int manufacturerImageQualityRank,
        String manufacturerImageQualityVerifiedAt,
        String manufacturerImageQualityLimitationCode,
        String manufacturerImageQualityLimitationDetail,
        String manufacturerImageQualityLimitationObservedAt,
        int volumeMl,
        String colorFamily,
        String manufacturerDescription,
        List<String> recommendedUses,
        UsageInstructions usageInstructions,
        String manufacturerVerifiedAt,
        String resultImage,
        String resultImageCredit,
        String resultImageSource,
        String resultImageLicense,
        String resultReferenceUrl) {

    public MarketPaintView {
        brandAliases = List.copyOf(brandAliases);
        tags = List.copyOf(tags);
        recommendedUses = List.copyOf(recommendedUses);
    }

    /** Brand-independent characteristics used by search, facets and matching. */
    public record Profile(
            List<String> roles,
            List<String> applicationMethods,
            String applicationSystem,
            String coverage,
            String finish,
            List<String> effects,
            String undercoatTone,
            boolean preHighlightedSurfaceRecommended,
            String medium) {
        public Profile {
            roles = List.copyOf(roles);
            applicationMethods = List.copyOf(applicationMethods);
            effects = List.copyOf(effects);
        }
    }

    /** Explicit usage guidance, especially important for technical and behavior-driven paints. */
    public record UsageInstructions(
            String summary,
            List<String> steps,
            List<String> tips,
            String instructionStatus,
            boolean reviewRequired) {
        public UsageInstructions {
            steps = List.copyOf(steps);
            tips = List.copyOf(tips);
        }
    }
}
