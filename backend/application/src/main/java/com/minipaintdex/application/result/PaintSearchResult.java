package com.minipaintdex.application.result;

import com.minipaintdex.application.view.PaintProductSuggestion;
import java.util.List;

/** Unrequested parts are null; an empty requested part is a successful empty selection. */
public record PaintSearchResult<T>(PageResult<T> results, List<PaintProductSuggestion> suggestions, String correlationId) {
    public PaintSearchResult {
        suggestions = suggestions == null ? null : List.copyOf(suggestions);
    }
}
