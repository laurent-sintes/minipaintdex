package com.minipaintdex.application.query;

/** Transport-independent search limits and relevance weights, bound once at startup. */
public record PaintSearchPolicy(
        int defaultSuggestionLimit, int maxSuggestionLimit, int maxQueryLength, int maxTerms, int fuzzyMinLength, int maxEdits, int maxExpansions,
        float exactReferenceBoost, float exactNameBoost, float nameBoost,
        float catalogBoost, float metadataBoost, float prefixBoost, float fuzzyBoost) {
    public PaintSearchPolicy {
        if (defaultSuggestionLimit < 1 || defaultSuggestionLimit > maxSuggestionLimit || maxSuggestionLimit > 50
                || maxQueryLength < 1 || maxQueryLength > 1000 || maxTerms < 1 || maxTerms > 32
                || fuzzyMinLength < 3 || maxEdits < 0 || maxEdits > 2 || maxExpansions < 1 || maxExpansions > 100) {
            throw new IllegalArgumentException("Invalid paint search limits");
        }
        for (var boost : new float[]{exactReferenceBoost, exactNameBoost, nameBoost, catalogBoost,
                metadataBoost, prefixBoost, fuzzyBoost}) {
            if (!Float.isFinite(boost) || boost <= 0) throw new IllegalArgumentException("Search boosts must be positive and finite");
        }
        if (exactReferenceBoost <= exactNameBoost || exactNameBoost <= nameBoost
                || nameBoost < catalogBoost || catalogBoost < metadataBoost || prefixBoost <= fuzzyBoost) {
            throw new IllegalArgumentException("Search weights must preserve exact-reference/name/prefix/fuzzy priorities");
        }
    }
}
