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
        var source = paint("source", "#A52A2A", "opaque_standard", Set.of());
        var close = paint("close", "#A62B2B", "opaque_standard", Set.of());
        var distant = paint("distant", "#1C4DA1", "opaque_standard", Set.of());

        var result = engine.rank(source, List.of(distant, close));

        assertEquals("close", result.getFirst().candidatePaintId());
        assertTrue(result.getFirst().deltaE2000() < result.getLast().deltaE2000());
        assertFalse(result.getFirst().requiresManualReview());
    }

    @Test
    void alwaysRequiresManualReviewForBehavioralPaints() {
        var source = paint("source", "#A52A2A", "one_coat_contrast", Set.of("pooling", "pigment_separation"));
        var candidate = paint("candidate", "#A52A2A", "one_coat_contrast", Set.of("pooling", "pigment_separation"));

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
        var source = paint("source", "", "opaque_standard", Set.of());
        var candidate = paint("candidate", "#777777", "opaque_standard", Set.of());

        var match = engine.compare(source, candidate);

        assertEquals(-1.0, match.deltaE2000());
        assertEquals(50.0, match.colorScore());
        assertTrue(match.reasons().contains("color_metadata_missing"));
        assertFalse(match.reasons().contains("close_color"));
    }

    private static PaintMatchEngine.Paint paint(String id, String hex, String type, Set<String> behavior) {
        return new PaintMatchEngine.Paint(id, hex, type, "matt", "opaque", "water acrylic", behavior);
    }

    private static PaintMatchingPolicy policy() {
        return new PaintMatchingPolicy(
                5,
                Set.of("one_coat_contrast", "technical_effect", "primer", "wash_shade", "ink", "auxiliary"),
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
