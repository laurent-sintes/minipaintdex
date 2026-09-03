package com.minipaintdex.application.validation;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.domain.shared.DomainException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Import-only safeguards. Source evidence never enters the searchable Market model. */
public final class PaintProductImportEvidence {
    private static final String REVIEW = "reviewed-paint-color-quality";
    private static final Set<String> REVIEW_FIELDS = Set.of(
            "color.hex", "color.family", "profile.finish", "profile.coverage", "profile.effects",
            "profile.roles", "profile.application_system", "profile.application_methods");

    private PaintProductImportEvidence() {}

    public static StructuredDocument preserve(StructuredDocument previous, StructuredDocument incoming) {
        var before = StructuredDocuments.toMap(previous);
        var after = StructuredDocuments.toMap(incoming);
        var oldSources = StructuredDocuments.maps(before.get("source_snapshots"));
        var newSources = StructuredDocuments.maps(after.get("source_snapshots"));
        var protectedFields = new LinkedHashSet<String>();
        if (!StructuredDocuments.text(value(before, "color.hex")).isBlank()) protectedFields.add("color.hex");
        for (var source : oldSources) {
            if (REVIEW.equals(source.get("provider"))) {
                var path = path(StructuredDocuments.map(source.get("payload")));
                if (REVIEW_FIELDS.contains(path)) protectedFields.add(path);
            }
        }
        var decisions = newSources.stream().filter(source -> REVIEW.equals(source.get("provider"))
                && !oldSources.contains(source)).toList();
        for (var source : decisions) {
            var payload = StructuredDocuments.map(source.get("payload"));
            var field = path(payload);
            if (!REVIEW_FIELDS.contains(field)
                    || !StructuredDocuments.text(source.get("url")).startsWith("https://")
                    || StructuredDocuments.text(payload.get("rationale")).isBlank()
                    || StructuredDocuments.text(payload.get("review_id")).isBlank()
                    || !StructuredDocuments.text(payload.get("manifest_sha256")).matches("[0-9a-f]{64}")
                    || !equivalent(field, payload.get("before"), value(before, field))
                    || !equivalent(field, payload.get("after"), value(after, field))) {
                throw new DomainException("conflict", "Stale or invalid paint quality review: " + before.get("id") + "/" + field);
            }
        }
        for (var field : protectedFields) {
            if (!equivalent(field, value(before, field), value(after, field))
                    && decisions.stream().noneMatch(source -> field.equals(path(StructuredDocuments.map(source.get("payload")))))) {
                throw new DomainException("conflict", "Paint quality change requires an explicit before/after review: " + before.get("id") + "/" + field);
            }
        }
        // Preserve complete historical observations even when a provider exports only its latest snapshot.
        var sources = new ArrayList<>(oldSources);
        newSources.stream().filter(source -> !sources.contains(source)).forEach(sources::add);
        var result = new LinkedHashMap<>(after);
        if (!sources.isEmpty()) result.put("source_snapshots", sources);
        return StructuredDocuments.fromMap(result);
    }

    private static String path(Map<String, Object> payload) {
        return switch (StructuredDocuments.text(payload.get("field"))) {
            case "family" -> "color.family";
            case "effects" -> "profile.effects";
            case String field -> field;
        };
    }

    private static Object value(Map<String, Object> document, String path) {
        var parts = path.split("\\.", 2);
        return parts.length == 2 ? StructuredDocuments.map(document.get(parts[0])).get(parts[1]) : null;
    }

    private static boolean equivalent(String path, Object left, Object right) {
        return "color.hex".equals(path) && left instanceof String a && right instanceof String b
                ? a.equalsIgnoreCase(b) : Objects.equals(left, right);
    }
}
