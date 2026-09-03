package com.minipaintdex.application.view;

import java.util.List;

/** Published canonical paint-model metadata used by schema and generic search adapters. */
public record PaintModelView(
        int modelVersion,
        String jsonSchemaDraft,
        List<Filter> filters,
        List<SortOption> sortOptions,
        List<Vocabulary> vocabularies) {

    public PaintModelView {
        filters = List.copyOf(filters);
        sortOptions = List.copyOf(sortOptions);
        vocabularies = List.copyOf(vocabularies);
    }

    public record Filter(
            String id,
            String queryParameter,
            String facetId,
            String labelKey,
            String vocabularyId,
            String control,
            String group,
            int order) {}

    public record SortOption(
            String id,
            String queryValue,
            String labelKey,
            int order) {}

    public record Vocabulary(String id, List<String> values) {
        public Vocabulary { values = List.copyOf(values); }
    }
}
