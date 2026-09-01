package com.minipaintdex.application.view;

import java.util.List;

/** Published canonical paint-model metadata used by schema and generic search adapters. */
public record PaintModelView(
        int modelVersion,
        String jsonSchemaDraft,
        List<Filter> filters,
        List<Vocabulary> vocabularies) {

    public PaintModelView {
        filters = List.copyOf(filters);
        vocabularies = List.copyOf(vocabularies);
    }

    public record Filter(
            String id,
            String queryParameter,
            String facetId,
            String labelKey,
            String vocabularyId,
            int order) {}

    public record Vocabulary(String id, List<String> values) {
        public Vocabulary { values = List.copyOf(values); }
    }
}
