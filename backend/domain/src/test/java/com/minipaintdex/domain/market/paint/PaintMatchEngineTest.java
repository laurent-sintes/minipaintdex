package com.minipaintdex.domain.market.paint;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintMatchEngineTest {
    private final PaintMatchEngine engine = new PaintMatchEngine(policy());

    @Test
    void ranksStandardPaintsPrimarilyByCiede2000ColorDistance() {
        var source = paint("source", "#A52A2A", "conventional_layering", Set.of());
        var close = paint("close", "#A62B2B", "conventional_layering", Set.of());
        var distant = paint("distant", "#1C4DA1", "conventional_layering", Set.of());

        var result = engine.rank(source, List.of(distant, close));

        assertEquals("close", result.getFirst().candidatePaintId());
        assertTrue(result.getFirst().deltaE2000() < result.getLast().deltaE2000());
        assertFalse(result.getFirst().requiresManualReview());
    }

    @Test
    void alwaysRequiresManualReviewForBehavioralPaints() {
        var source = paint("source", "#A52A2A", "one_coat_shading", Set.of("metallic"));
        var candidate = paint("candidate", "#A52A2A", "one_coat_shading", Set.of("metallic"));

        var match = engine.compare(source, candidate);

        assertTrue(match.requiresManualReview());
        assertEquals("manual_technique_review", match.strategy());
        assertTrue(match.reasons().contains("similar_application_behavior"));
    }

    @Test
    void rejectsWeightSetsThatAreNotNormalized() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaintMatchingPolicy.Weights(.60, .20, 0, .10, .05, .01));
    }

    @Test
    void treatsUnknownColorsAsMissingMetadataInsteadOfNeutralGray() {
        var source = paint("source", "", "conventional_layering", Set.of());
        var candidate = paint("candidate", "#777777", "conventional_layering", Set.of());

        var match = engine.compare(source, candidate);

        assertEquals(-1.0, match.deltaE2000());
        assertEquals(50.0, match.colorScore());
        assertTrue(match.reasons().contains("color_metadata_missing"));
        assertFalse(match.reasons().contains("close_color"));
    }

    private static PaintMatchEngine.Paint paint(String id, String hex, String system, Set<String> effects) {
        return new PaintMatchEngine.Paint(
                id, hex, Set.of("color_paint"), system, "matte", "opaque",
                "water_based_acrylic", effects);
    }

    private static PaintMatchingPolicy policy() {
        return new PaintMatchingPolicy(
                5,
                Set.of("one_coat_shading", "washing", "priming", "effect_application"),
                2.5,
                20,
                25,
                50,
                50,
                80,
                75,
                new PaintMatchingPolicy.Weights(.65, .15, 0, .08, .07, .05),
                new PaintMatchingPolicy.Weights(.15, .35, .30, .10, .10, 0));
    }
}
