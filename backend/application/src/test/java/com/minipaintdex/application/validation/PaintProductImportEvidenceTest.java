package com.minipaintdex.application.validation;

import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaintProductImportEvidenceTest {
    private static Map<String, Object> paint(String hex) {
        return new LinkedHashMap<>(Map.of("id", "paint-1", "color", Map.of("hex", hex),
                "profile", Map.of("finish", "satin", "effects", List.of("metallic"))));
    }

    private static Map<String, Object> review(String field, Object before, Object after) {
        return Map.of("provider", "reviewed-paint-color-quality", "url", "https://example.test/chart",
                "payload", Map.of("field", field, "before", before, "after", after,
                        "rationale", "Reviewed manufacturer evidence", "review_id", "review-1",
                        "manifest_sha256", "a".repeat(64)));
    }

    @Test void retainsHistoricalSourcesAndIsIdempotentWithoutMutatingInput() {
        var previous = paint("#112233");
        var proof = review("profile.finish", "unknown", "satin");
        previous.put("source_snapshots", List.of(proof));
        var incoming = paint("#112233");
        var current = StructuredDocuments.fromMap(previous);
        var result = PaintProductImportEvidence.preserve(current, StructuredDocuments.fromMap(incoming));
        assertEquals(current, result);
        assertEquals(result, PaintProductImportEvidence.preserve(result, result));
        assertFalse(incoming.containsKey("source_snapshots"));
    }

    @Test void refusesHexAndReviewedProfileRegressionEvenWhenEvidenceIsOmitted() {
        var previous = paint("#112233");
        previous.put("source_snapshots", List.of(review("profile.finish", "unknown", "satin"),
                review("effects", List.of(), List.of("metallic"))));
        var current = StructuredDocuments.fromMap(previous);
        assertThrows(DomainException.class, () -> PaintProductImportEvidence.preserve(current, StructuredDocuments.fromMap(paint("#ffffff"))));
        for (var profile : List.of(Map.of("finish", "unknown", "effects", List.of("metallic")),
                Map.of("finish", "satin", "effects", List.of()))) {
            var incoming = paint("#112233"); incoming.put("profile", profile);
            assertThrows(DomainException.class, () -> PaintProductImportEvidence.preserve(current, StructuredDocuments.fromMap(incoming)));
        }
    }

    @Test void acceptsOnlyFreshExplicitReviewAndRetainsOldEvidence() {
        var previous = paint("#112233");
        previous.put("source_snapshots", List.of(Map.of("provider", "original", "payload", Map.of("hex", "#112233"))));
        var incoming = paint("#aabbcc");
        incoming.put("source_snapshots", List.of(review("color.hex", "#112233", "#aabbcc")));
        var result = PaintProductImportEvidence.preserve(StructuredDocuments.fromMap(previous), StructuredDocuments.fromMap(incoming));
        assertEquals(2, StructuredDocuments.maps(StructuredDocuments.toMap(result).get("source_snapshots")).size());
        assertEquals(result, PaintProductImportEvidence.preserve(result, result));
        incoming.put("source_snapshots", List.of(review("color.hex", "#000000", "#aabbcc")));
        assertThrows(DomainException.class, () -> PaintProductImportEvidence.preserve(StructuredDocuments.fromMap(previous), StructuredDocuments.fromMap(incoming)));
    }
}
