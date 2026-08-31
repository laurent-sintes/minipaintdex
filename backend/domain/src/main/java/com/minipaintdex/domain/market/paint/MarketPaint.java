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
        MarketPaintType functionalType,
        String reference,
        String name,
        Color color,
        String finish,
        String medium,
        String opacity,
        MarketPaintLifecycle lifecycle,
        String dataStatus,
        List<String> warnings,
        List<String> tags,
        List<String> behaviorTags,
        String notes,
        URI manufacturerPage,
        ImageReference manufacturerImage,
        int volumeMl,
        List<String> recommendedUses,
        UsageInstructions usageInstructions,
        LocalDate verifiedAt,
        ImageReference resultImage) {

    public MarketPaint {
        if (schemaVersion < 1) throw invalid("schemaVersion must be positive.");
        id = stableId(id, "id");
        brand = required(brand, "brand");
        manufacturer = required(manufacturer, "manufacturer");
        brandAliases = immutableStrings(brandAliases);
        range = required(range, "range");
        if (functionalType == null) throw invalid("functionalType is required.");
        reference = optional(reference);
        name = required(name, "name");
        color = color == null ? new Color(null, null) : color;
        finish = optional(finish);
        medium = optional(medium);
        opacity = optional(opacity);
        lifecycle = lifecycle == null ? MarketPaintLifecycle.UNKNOWN : lifecycle;
        dataStatus = required(dataStatus, "dataStatus");
        warnings = immutableStrings(warnings);
        tags = immutableStrings(tags);
        behaviorTags = immutableStrings(behaviorTags);
        notes = optional(notes);
        manufacturerImage = manufacturerImage == null ? ImageReference.empty() : manufacturerImage;
        if (volumeMl < 0) throw invalid("volumeMl cannot be negative.");
        recommendedUses = immutableStrings(recommendedUses);
        usageInstructions = usageInstructions == null ? UsageInstructions.empty() : usageInstructions;
        resultImage = resultImage == null ? ImageReference.empty() : resultImage;
        if (functionalType.requiresUsageInstructions() && !usageInstructions.complete()) {
            throw invalid("Paint type " + functionalType.id() + " requires a summary and at least one usage step.");
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
            URI referenceUrl) {
        public ImageReference {
            path = optional(path);
            credit = optional(credit);
            license = optional(license);
        }

        public static ImageReference empty() {
            return new ImageReference(null, null, null, null, null);
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
