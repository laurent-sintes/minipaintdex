package com.minipaintdex.domain.market.paint;

import com.minipaintdex.domain.shared.DomainException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Shared, file-versioned Market instructions; translations never alter source authority. */
public record PaintUsageGuide(int schemaVersion, String id, String brand, String title, int revision,
        List<String> ranges, String originalLanguage, Content original, String knowledgeStatus,
        boolean reviewRequired, List<URI> sourceUrls, List<Translation> translations) {
    public PaintUsageGuide {
        if (schemaVersion != 1 || revision < 1) throw invalid("Guide schema must be 1 and revision positive");
        if (id == null || !id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw invalid("Invalid guide ID");
        brand = required(brand); title = required(title); knowledgeStatus = required(knowledgeStatus); originalLanguage = required(originalLanguage); if (!Set.of("en", "fr", "mul").contains(originalLanguage)) throw invalid("Invalid original language");
        ranges = strings(ranges);
        if (ranges.isEmpty()) throw invalid("A guide needs explicit range scope");
        if (original == null || sourceUrls == null || translations == null) throw invalid("Guide content, sources and translations are required");
        if (!original.present()) throw invalid("Guide content cannot be empty");
        if (!Set.of("manufacturer", "sourced-summary", "generic-template", "unverified").contains(knowledgeStatus)) {
            throw invalid("Invalid guide knowledge status");
        }
        if (Set.of("generic-template", "unverified").contains(knowledgeStatus) && !reviewRequired) {
            throw invalid("Generic or unverified instructions require review");
        }
        sourceUrls = List.copyOf(sourceUrls);
        for (var url : sourceUrls) if (url == null || !Set.of("http", "https").contains(url.getScheme()) || url.getHost() == null) {
            throw invalid("Guide sources must be HTTP(S) URLs");
        }
        if (Set.of("manufacturer", "sourced-summary").contains(knowledgeStatus) && sourceUrls.isEmpty()) {
            throw invalid("Sourced instructions require a source URL");
        }
        translations = List.copyOf(translations);
        if (translations.stream().map(Translation::language).distinct().count() != translations.size()) throw invalid("Duplicate translation language");
        for (var translation : translations) {
            if (translation.language().equals(originalLanguage) || translation.sourceRevision() > revision) {
                throw invalid("Translation must target a different language and an existing source revision");
            }
            if (translation.sourceRevision() == revision && (translation.content().steps().size() != original.steps().size()
                    || translation.content().tips().size() != original.tips().size()
                    || translation.content().summary().isBlank() != original.summary().isBlank())) {
                throw invalid("A current translation must preserve the summary, every step and every precaution");
            }
        }
    }

    public boolean appliesTo(PaintProduct paint) { return brand.equals(paint.brand()) && ranges.contains(paint.range()); }

    /** Content/scope changes advance exactly one revision; same-revision translation corrections are allowed. */
    public void validateReplacement(PaintUsageGuide next) {
        if (!id.equals(next.id) || !brand.equals(next.brand)) throw conflict("Guide identity and brand are immutable");
        var sourceChanged = !title.equals(next.title) || !ranges.equals(next.ranges)
                || !originalLanguage.equals(next.originalLanguage) || !original.equals(next.original)
                || !knowledgeStatus.equals(next.knowledgeStatus) || reviewRequired != next.reviewRequired
                || !sourceUrls.equals(next.sourceUrls);
        if (next.revision != revision + (sourceChanged ? 1 : 0)) throw conflict("Guide source changes require the next revision");
    }

    public Selection select(String requestedLanguage) {
        var requested = language(requestedLanguage);
        if (requested.equals("original") || requested.equals(originalLanguage)) return new Selection(originalLanguage, original, "original", false);
        var translation = translations.stream().filter(t -> t.language().equals(requested)).findFirst().orElse(null);
        if (translation != null && translation.sourceRevision() == revision) {
            return new Selection(requested, translation.content(), translation.method(), translation.reviewRequired());
        }
        return new Selection(originalLanguage, original, translation == null ? "missing-translation" : "stale-translation", false);
    }

    public record Content(String summary, List<String> steps, List<String> tips) {
        public Content { summary = summary == null ? "" : summary.trim(); steps = strings(steps); tips = strings(tips); }
        public boolean present() { return !summary.isBlank() || !steps.isEmpty() || !tips.isEmpty(); }
    }
    public record Translation(String language, int sourceRevision, String method, boolean reviewRequired, Content content) {
        public Translation {
            method = required(method);
            language = PaintUsageGuide.language(language);
            if (language.equals("original")) throw invalid("A translation needs a concrete language");
            if (sourceRevision < 1 || !Set.of("machine", "human").contains(method)) throw invalid("Invalid translation metadata");
            if (method.equals("machine") && !reviewRequired) throw invalid("Machine translation requires review");
            if (content == null || !content.present()) throw invalid("Translation cannot be empty");
        }
    }
    public record Selection(String language, Content content, String translationStatus, boolean translationReviewRequired) {}
    public static String language(String value) {
        if (value == null || !Set.of("en", "fr", "original").contains(value)) throw invalid("Supported guide languages are en and fr");
        return value;
    }
    private static String required(String value) { if (value == null || value.isBlank()) throw invalid("Required guide text is blank"); return value.trim(); }
    private static List<String> strings(List<String> values) { return List.copyOf(values).stream().map(PaintUsageGuide::required).toList(); }
    private static DomainException invalid(String message) { return new DomainException("invalid_input", message); }
    private static DomainException conflict(String message) { return new DomainException("conflict", message); }
}
