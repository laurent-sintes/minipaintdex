package com.minipaintdex.application.view;

import java.util.List;

/** Immutable read model exposed by the market-paint application port. */
public record MarketPaintView(
        String id,
        String brand,
        String manufacturer,
        List<String> brandAliases,
        String range,
        String paintType,
        String reference,
        String name,
        String colorHex,
        String finish,
        String medium,
        String opacity,
        String lifecycleStatus,
        String status,
        String warnings,
        List<String> tags,
        String notes,
        String createdAt,
        String updatedAt,
        String manufacturerUrl,
        String manufacturerImage,
        String manufacturerImageCredit,
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
