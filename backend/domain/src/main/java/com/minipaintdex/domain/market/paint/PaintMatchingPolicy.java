package com.minipaintdex.domain.market.paint;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record PaintMatchingPolicy(
        int candidateLimit,
        Set<String> behavioralSystems,
        double colorDistanceFactor,
        double roleMismatchScore,
        double metadataMismatchScore,
        double missingMetadataScore,
        double emptyBehaviorScore,
        double closeColorThreshold,
        double similarBehaviorThreshold,
        Weights standard,
        Weights behavioral) {

    public PaintMatchingPolicy {
        if (candidateLimit < 1) throw new IllegalArgumentException("candidateLimit must be positive");
        behavioralSystems = Objects.requireNonNull(behavioralSystems).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (behavioralSystems.isEmpty()) throw new IllegalArgumentException("behavioralSystems must not be empty");
        if (colorDistanceFactor <= 0) throw new IllegalArgumentException("colorDistanceFactor must be positive");
        validateScore("roleMismatchScore", roleMismatchScore);
        validateScore("metadataMismatchScore", metadataMismatchScore);
        validateScore("missingMetadataScore", missingMetadataScore);
        validateScore("emptyBehaviorScore", emptyBehaviorScore);
        validateScore("closeColorThreshold", closeColorThreshold);
        validateScore("similarBehaviorThreshold", similarBehaviorThreshold);
        standard = Objects.requireNonNull(standard);
        behavioral = Objects.requireNonNull(behavioral);
    }

    public boolean isBehavioral(String applicationSystem) {
        return applicationSystem != null
                && behavioralSystems.contains(applicationSystem.toLowerCase(Locale.ROOT));
    }

    private static void validateScore(String name, double score) {
        if (score < 0 || score > 100) throw new IllegalArgumentException(name + " must be between 0 and 100");
    }

    public record Weights(
            double color,
            double role,
            double behavior,
            double finish,
            double coverage,
            double medium) {
        public Weights {
            if (color < 0 || role < 0 || behavior < 0 || finish < 0 || coverage < 0 || medium < 0) {
                throw new IllegalArgumentException("Paint matching weights must not be negative");
            }
            var total = color + role + behavior + finish + coverage + medium;
            if (Math.abs(total - 1.0) > 0.000_001) {
                throw new IllegalArgumentException("Paint matching weights must sum to 1.0, got " + total);
            }
        }

        public double score(
                double colorScore,
                double roleScore,
                double behaviorScore,
                double finishScore,
                double coverageScore,
                double mediumScore) {
            return color * colorScore
                    + role * roleScore
                    + behavior * behaviorScore
                    + finish * finishScore
                    + coverage * coverageScore
                    + medium * mediumScore;
        }
    }
}
