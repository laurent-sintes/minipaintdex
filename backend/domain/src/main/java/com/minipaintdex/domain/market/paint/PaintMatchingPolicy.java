package com.minipaintdex.domain.market.paint;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record PaintMatchingPolicy(
        int candidateLimit,
        Set<String> behavioralTypes,
        double colorDistanceFactor,
        double functionalTypeMismatchScore,
        double metadataMismatchScore,
        double missingMetadataScore,
        double emptyBehaviorScore,
        double closeColorThreshold,
        double similarBehaviorThreshold,
        Weights standard,
        Weights behavioral) {

    public PaintMatchingPolicy {
        if (candidateLimit < 1) throw new IllegalArgumentException("candidateLimit must be positive");
        behavioralTypes = Objects.requireNonNull(behavioralTypes).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (behavioralTypes.isEmpty()) throw new IllegalArgumentException("behavioralTypes must not be empty");
        if (colorDistanceFactor <= 0) throw new IllegalArgumentException("colorDistanceFactor must be positive");
        validateScore("functionalTypeMismatchScore", functionalTypeMismatchScore);
        validateScore("metadataMismatchScore", metadataMismatchScore);
        validateScore("missingMetadataScore", missingMetadataScore);
        validateScore("emptyBehaviorScore", emptyBehaviorScore);
        validateScore("closeColorThreshold", closeColorThreshold);
        validateScore("similarBehaviorThreshold", similarBehaviorThreshold);
        standard = Objects.requireNonNull(standard);
        behavioral = Objects.requireNonNull(behavioral);
    }

    public boolean isBehavioral(String functionalType) {
        return functionalType != null && behavioralTypes.contains(functionalType.toLowerCase(Locale.ROOT));
    }

    private static void validateScore(String name, double score) {
        if (score < 0 || score > 100) throw new IllegalArgumentException(name + " must be between 0 and 100");
    }

    public record Weights(
            double color,
            double functionalType,
            double behavior,
            double finish,
            double opacity,
            double medium) {
        public Weights {
            if (color < 0 || functionalType < 0 || behavior < 0 || finish < 0 || opacity < 0 || medium < 0) {
                throw new IllegalArgumentException("Paint matching weights must not be negative");
            }
            var total = color + functionalType + behavior + finish + opacity + medium;
            if (Math.abs(total - 1.0) > 0.000_001) {
                throw new IllegalArgumentException("Paint matching weights must sum to 1.0, got " + total);
            }
        }

        public double score(
                double colorScore,
                double functionalTypeScore,
                double behaviorScore,
                double finishScore,
                double opacityScore,
                double mediumScore) {
            return color * colorScore
                    + functionalType * functionalTypeScore
                    + behavior * behaviorScore
                    + finish * finishScore
                    + opacity * opacityScore
                    + medium * mediumScore;
        }
    }
}
