package com.minipaintdex.domain.paint;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaintMatchEngineTest {
    private final PaintMatchEngine engine = new PaintMatchEngine();

    @Test
    void ranksStandardPaintsPrimarilyByCiede2000ColorDistance() {
        var source = paint("source", "#A52A2A", "opaque_standard", Set.of());
        var close = paint("close", "#A62B2B", "opaque_standard", Set.of());
        var distant = paint("distant", "#1C4DA1", "opaque_standard", Set.of());

        var result = engine.rank(source, List.of(distant, close), 5);

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

    private static PaintMatchEngine.Paint paint(String id, String hex, String type, Set<String> behavior) {
        return new PaintMatchEngine.Paint(id, hex, type, "matt", "opaque", "water acrylic", behavior);
    }
}
