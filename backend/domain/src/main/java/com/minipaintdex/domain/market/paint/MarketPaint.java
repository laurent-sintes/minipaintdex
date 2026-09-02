package com.minipaintdex.domain.market.paint;

import com.minipaintdex.domain.shared.DomainException;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** Aggregate root for one validated commercial paint reference in the Market catalog. */
public record MarketPaint(
        int schemaVersion,
        String id,
        String brand,
        String manufacturer,
        List<String> brandAliases,
        String range,
        MarketPaintProfile profile,
        String reference,
        String name,
        Color color,
        MarketPaintLifecycle lifecycle,
        String dataStatus,
        List<String> warnings,
        List<String> tags,
        String notes,
        URI manufacturerPage,
        ImageReference manufacturerImage,
        int volumeMl,
        List<String> recommendedUses,
        UsageInstructions usageInstructions,
        LocalDate verifiedAt,
        ImageReference resultImage) {

    public MarketPaint {
        if (schemaVersion != 1) throw invalid("schemaVersion must be 1.");
        id = stableId(id, "id");
        brand = required(brand, "brand");
        manufacturer = required(manufacturer, "manufacturer");
        brandAliases = immutableStrings(brandAliases);
        range = required(range, "range");
        if (profile == null) throw invalid("profile is required.");
        reference = optional(reference);
        name = required(name, "name");
        color = color == null ? new Color(null, null) : color;
        lifecycle = lifecycle == null ? MarketPaintLifecycle.UNKNOWN : lifecycle;
        dataStatus = required(dataStatus, "dataStatus");
        warnings = immutableStrings(warnings);
        tags = immutableStrings(tags);
        notes = optional(notes);
        manufacturerImage = manufacturerImage == null ? ImageReference.empty() : manufacturerImage;
        if (manufacturerImage.imageQuality() == MarketPaintImageQuality.OFFICIAL_PHOTO
                && manufacturerImage.qualityLimitation() != null) {
            throw invalid("manufacturerImage.qualityLimitation must be absent for an official photo.");
        }
        if (manufacturerImage.imageQuality() != MarketPaintImageQuality.OFFICIAL_PHOTO
                && manufacturerImage.qualityLimitation() == null) {
            throw invalid("manufacturerImage.qualityLimitation is required when image quality is not official_photo.");
        }
        if (volumeMl < 0) throw invalid("volumeMl cannot be negative.");
        recommendedUses = immutableStrings(recommendedUses);
        usageInstructions = usageInstructions == null ? UsageInstructions.empty() : usageInstructions;
        resultImage = resultImage == null ? ImageReference.empty() : resultImage;
        if (profile.requiresUsageInstructions() && !usageInstructions.complete()) {
            throw invalid("Paint roles " + profile.roleIds() + " require a summary and at least one usage step.");
        }
    }

    public record Color(String family, String hex) {
        public Color {
            family = optional(family);
            hex = optional(hex);
            if (hex != null && !hex.matches("#[0-9A-Fa-f]{6}")) throw invalid("color.hex must use #RRGGBB.");
            if (hex != null) hex = hex.toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record ImageReference(
            String path,
            URI sourceUrl,
            String credit,
            String license,
            URI referenceUrl,
            MarketPaintImageQuality imageQuality,
            LocalDate qualityVerifiedAt,
            ImageQualityLimitation qualityLimitation) {
        public ImageReference {
            path = optional(path);
            credit = optional(credit);
            license = optional(license);
            imageQuality = imageQuality == null ? MarketPaintImageQuality.NONE : imageQuality;
            if (sourceUrl != null && !"https".equalsIgnoreCase(sourceUrl.getScheme())) {
                throw invalid("image sourceUrl must use HTTPS.");
            }
            if (referenceUrl != null && !"https".equalsIgnoreCase(referenceUrl.getScheme())) {
                throw invalid("image referenceUrl must use HTTPS.");
            }
            var sourcedVisual = imageQuality == MarketPaintImageQuality.OFFICIAL_PHOTO
                    || imageQuality == MarketPaintImageQuality.RETAILER_PHOTO
                    || imageQuality == MarketPaintImageQuality.OWNED_PHOTO
                    || imageQuality == MarketPaintImageQuality.GENERIC_VISUAL;
            if (sourcedVisual && path == null && sourceUrl == null) {
                throw invalid("image path or sourceUrl is required for " + imageQuality.id() + ".");
            }
            if (imageQuality != MarketPaintImageQuality.NONE && qualityVerifiedAt == null) {
                throw invalid("image qualityVerifiedAt is required for " + imageQuality.id() + ".");
            }
            if (imageQuality == MarketPaintImageQuality.RETAILER_PHOTO
                    && (credit == null || referenceUrl == null)) {
                throw invalid("retailer photos require a credit and referenceUrl.");
            }
            if (imageQuality == MarketPaintImageQuality.OFFICIAL_PHOTO && qualityLimitation != null) {
                throw invalid("qualityLimitation must be absent for an official photo.");
            }
        }

        public static ImageReference empty() {
            return new ImageReference(null, null, null, null, null, MarketPaintImageQuality.NONE, null, null);
        }
    }

    public record ImageQualityLimitation(
            MarketPaintImageLimitationCode code,
            String detail,
            LocalDate observedAt) {
        public ImageQualityLimitation {
            if (code == null) throw invalid("image quality limitation code is required.");
            detail = required(detail, "image quality limitation detail");
            if (observedAt == null) throw invalid("image quality limitation observedAt is required.");
        }
    }

    public record UsageInstructions(
            String summary,
            List<String> steps,
            List<String> tips,
            String instructionStatus,
            boolean reviewRequired) {
        public UsageInstructions {
            summary = optional(summary);
            steps = immutableStrings(steps);
            tips = immutableStrings(tips);
            instructionStatus = optional(instructionStatus);
        }

        public boolean complete() {
            return summary != null && !steps.isEmpty();
        }

        public static UsageInstructions empty() {
            return new UsageInstructions(null, List.of(), List.of(), null, false);
        }
    }

    private static List<String> immutableStrings(List<String> values) {
        if (values == null) return List.of();
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw invalid("String collections cannot contain blank values.");
        }
        return List.copyOf(values);
    }

    private static String stableId(String value, String field) {
        var result = required(value, field);
        if (!result.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw invalid(field + " must be a lowercase ASCII kebab-case identifier.");
        }
        return result;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " is required.");
        return value;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static DomainException invalid(String message) {
        return new DomainException("invalid_market_paint", message);
    }
}
