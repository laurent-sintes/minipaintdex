package com.minipaintdex.application.query;

import com.minipaintdex.domain.shared.DomainException;
import java.util.Objects;
import java.util.Set;

/** Common read intent: optional result page and/or bounded suggestions over the same selection. */
public record PaintSearchQuery(SearchPaintProductsQuery filters, boolean manufacturerSheetOnly,
        boolean realResultOnly, Set<String> include, PageQuery page, Integer suggestionLimit, String correlationId) {
    public PaintSearchQuery {
        Objects.requireNonNull(filters);
        if (include != null && include.stream().anyMatch(Objects::isNull))
            throw new DomainException("invalid_input", "include cannot contain null.");
        include = include == null ? Set.of("results") : Set.copyOf(include);
        if (include.isEmpty() || !Set.of("results", "suggestions").containsAll(include))
            throw new DomainException("invalid_input", "include must select results, suggestions, or both.");
        if (include.contains("results") && page == null)
            throw new DomainException("invalid_input", "Result paging is required.");
        if (suggestionLimit != null && suggestionLimit < 1)
            throw new DomainException("invalid_input", "suggestionLimit must be positive.");
        if (correlationId == null || correlationId.isBlank())
            throw new DomainException("invalid_input", "correlationId is required.");
    }

    public boolean includesResults() { return include.contains("results"); }
    public boolean includesSuggestions() { return include.contains("suggestions"); }
    public int limit(PaintSearchPolicy policy) {
        if (suggestionLimit != null && suggestionLimit > policy.maxSuggestionLimit())
            throw new DomainException("invalid_input", "suggestionLimit exceeds " + policy.maxSuggestionLimit());
        return suggestionLimit == null ? policy.defaultSuggestionLimit() : suggestionLimit;
    }
}
