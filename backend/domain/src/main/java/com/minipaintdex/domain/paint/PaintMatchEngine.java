package com.minipaintdex.domain.paint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class PaintMatchEngine {
    private static final double PERFECT_SCORE = 100.0;
    private final PaintMatchingPolicy policy;

    public PaintMatchEngine(PaintMatchingPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    public PaintMatchingPolicy policy() {
        return policy;
    }

    public List<Match> rank(Paint source, List<Paint> candidates) {
        if (source == null) return List.of();
        return candidates.stream()
                .map(candidate -> compare(source, candidate))
                .sorted(Comparator.comparingDouble(Match::score).reversed())
                .limit(policy.candidateLimit())
                .toList();
    }

    public Match compare(Paint source, Paint candidate) {
        var behavioral = policy.isBehavioral(source.functionalType());
        var reasons = new ArrayList<String>();
        var deltaE = deltaE2000(lab(source.hex()), lab(candidate.hex()));
        var colorScore = clamp(PERFECT_SCORE - deltaE * policy.colorDistanceFactor());
        var typeScore = equals(source.functionalType(), candidate.functionalType())
                ? PERFECT_SCORE : policy.functionalTypeMismatchScore();
        var finishScore = comparableScore(source.finish(), candidate.finish());
        var opacityScore = comparableScore(source.opacity(), candidate.opacity());
        var mediumScore = comparableScore(source.medium(), candidate.medium());
        var behaviorScore = tagOverlap(source.behaviorTags(), candidate.behaviorTags());

        if (colorScore >= policy.closeColorThreshold()) reasons.add("close_color");
        if (typeScore == PERFECT_SCORE) reasons.add("same_functional_type");
        if (finishScore == PERFECT_SCORE) reasons.add("same_finish");
        if (behaviorScore >= policy.similarBehaviorThreshold()) reasons.add("similar_application_behavior");

        var weights = behavioral ? policy.behavioral() : policy.standard();
        var score = weights.score(colorScore, typeScore, behaviorScore, finishScore, opacityScore, mediumScore);
        if (behavioral) reasons.add("manual_behavior_review_required");
        return new Match(candidate.id(), round(score), round(deltaE), behavioral,
                behavioral ? "manual_technique_review" : "single_paint_candidate",
                round(colorScore), round(typeScore), round(behaviorScore), round(finishScore),
                round(opacityScore), round(mediumScore), List.copyOf(reasons));
    }

    public boolean requiresManualReview(Paint paint) {
        return paint != null && policy.isBehavioral(paint.functionalType());
    }

    private double comparableScore(String left, String right) {
        if (blank(left) || blank(right)) return policy.missingMetadataScore();
        return equals(left, right) ? PERFECT_SCORE : policy.metadataMismatchScore();
    }

    private double tagOverlap(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return policy.emptyBehaviorScore();
        var common = left.stream().filter(right::contains).count();
        var union = left.size() + right.size() - common;
        return union == 0 ? policy.emptyBehaviorScore() : PERFECT_SCORE * common / union;
    }

    private static boolean equals(String left, String right) {
        return !blank(left) && left.equalsIgnoreCase(right);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double[] lab(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) return new double[]{50, 0, 0};
        var r = Integer.parseInt(hex.substring(1, 3), 16) / 255.0;
        var g = Integer.parseInt(hex.substring(3, 5), 16) / 255.0;
        var b = Integer.parseInt(hex.substring(5, 7), 16) / 255.0;
        r = r <= .04045 ? r / 12.92 : Math.pow((r + .055) / 1.055, 2.4);
        g = g <= .04045 ? g / 12.92 : Math.pow((g + .055) / 1.055, 2.4);
        b = b <= .04045 ? b / 12.92 : Math.pow((b + .055) / 1.055, 2.4);
        var x = (r * .4124 + g * .3576 + b * .1805) / .95047;
        var y = (r * .2126 + g * .7152 + b * .0722);
        var z = (r * .0193 + g * .1192 + b * .9505) / 1.08883;
        x = xyz(x); y = xyz(y); z = xyz(z);
        return new double[]{116 * y - 16, 500 * (x - y), 200 * (y - z)};
    }

    private static double xyz(double value) {
        return value > .008856 ? Math.cbrt(value) : 7.787 * value + 16.0 / 116.0;
    }

    // CIEDE2000, with unit lightness/chroma/hue weights.
    private static double deltaE2000(double[] lab1, double[] lab2) {
        var l1 = lab1[0]; var a1 = lab1[1]; var b1 = lab1[2];
        var l2 = lab2[0]; var a2 = lab2[1]; var b2 = lab2[2];
        var c1 = Math.hypot(a1, b1); var c2 = Math.hypot(a2, b2);
        var cBar = (c1 + c2) / 2.0;
        var g = 0.5 * (1 - Math.sqrt(Math.pow(cBar, 7) / (Math.pow(cBar, 7) + Math.pow(25, 7))));
        var ap1 = (1 + g) * a1; var ap2 = (1 + g) * a2;
        var cp1 = Math.hypot(ap1, b1); var cp2 = Math.hypot(ap2, b2);
        var hp1 = hue(ap1, b1); var hp2 = hue(ap2, b2);
        var dl = l2 - l1; var dc = cp2 - cp1;
        var dhDegrees = hp2 - hp1;
        if (cp1 * cp2 == 0) dhDegrees = 0;
        else if (dhDegrees > 180) dhDegrees -= 360;
        else if (dhDegrees < -180) dhDegrees += 360;
        var dh = 2 * Math.sqrt(cp1 * cp2) * Math.sin(Math.toRadians(dhDegrees / 2));
        var lBar = (l1 + l2) / 2.0; var cpBar = (cp1 + cp2) / 2.0;
        double hpBar;
        if (cp1 * cp2 == 0) hpBar = hp1 + hp2;
        else if (Math.abs(hp1 - hp2) <= 180) hpBar = (hp1 + hp2) / 2.0;
        else if (hp1 + hp2 < 360) hpBar = (hp1 + hp2 + 360) / 2.0;
        else hpBar = (hp1 + hp2 - 360) / 2.0;
        var t = 1 - .17 * Math.cos(Math.toRadians(hpBar - 30))
                + .24 * Math.cos(Math.toRadians(2 * hpBar))
                + .32 * Math.cos(Math.toRadians(3 * hpBar + 6))
                - .20 * Math.cos(Math.toRadians(4 * hpBar - 63));
        var sl = 1 + .015 * Math.pow(lBar - 50, 2) / Math.sqrt(20 + Math.pow(lBar - 50, 2));
        var sc = 1 + .045 * cpBar; var sh = 1 + .015 * cpBar * t;
        var deltaTheta = 30 * Math.exp(-Math.pow((hpBar - 275) / 25, 2));
        var rc = 2 * Math.sqrt(Math.pow(cpBar, 7) / (Math.pow(cpBar, 7) + Math.pow(25, 7)));
        var rt = -rc * Math.sin(Math.toRadians(2 * deltaTheta));
        var vl = dl / sl; var vc = dc / sc; var vh = dh / sh;
        return Math.sqrt(vl * vl + vc * vc + vh * vh + rt * vc * vh);
    }

    private static double hue(double a, double b) {
        if (a == 0 && b == 0) return 0;
        var degrees = Math.toDegrees(Math.atan2(b, a));
        return degrees < 0 ? degrees + 360 : degrees;
    }

    public record Paint(
            String id, String hex, String functionalType, String finish, String opacity, String medium,
            Set<String> behaviorTags) {
        public Paint {
            behaviorTags = behaviorTags == null ? Set.of() : behaviorTags.stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    public record Match(
            String candidatePaintId, double score, double deltaE2000, boolean requiresManualReview,
            String strategy, double colorScore, double functionalTypeScore, double behaviorScore,
            double finishScore, double opacityScore, double mediumScore, List<String> reasons) {}
}
