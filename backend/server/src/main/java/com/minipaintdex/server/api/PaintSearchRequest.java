package com.minipaintdex.server.api;

import com.minipaintdex.application.query.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Set;

/** MiniPaintDex search contract, deliberately not an Elasticsearch query DSL. */
record PaintSearchRequest(String query, Filters filters,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Requested parts; omitted means results. Empty or unknown parts are invalid.", example = "[\"results\",\"suggestions\"]")
        Set<String> include, Integer suggestionLimit) {
    PaintSearchQuery toQuery(Pageable pageable, String correlationId) {
        var selections = filters == null ? Filters.empty() : filters;
        var page = new PageQuery(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().stream()
                .map(order -> new SortOrder(order.getProperty(), order.isAscending()
                        ? SortOrder.Direction.ASCENDING : SortOrder.Direction.DESCENDING)).toList());
        return new PaintSearchQuery(SearchPaintProductsQuery.fromSelections(query,
                selections.brand(), selections.range(), selections.role(), selections.applicationMethod(),
                selections.applicationSystem(), selections.color(), selections.finish(), selections.medium(),
                selections.coverage(), selections.effect(), selections.undercoat(), selections.lifecycle()),
                Boolean.TRUE.equals(selections.manufacturerSheetOnly()), Boolean.TRUE.equals(selections.realResultOnly()), include, page, suggestionLimit,
                correlationId == null || correlationId.isBlank() ? java.util.UUID.randomUUID().toString() : correlationId);
    }

    @JsonAnySetter
    void unknown(String name, Object ignored) { throw new IllegalArgumentException("Unknown search field: " + name); }

    record Filters(List<String> brand, List<String> range, List<String> role, List<String> applicationMethod,
            List<String> applicationSystem, List<String> color, List<String> finish, List<String> medium,
            List<String> coverage, List<String> effect, List<String> undercoat, List<String> lifecycle,
            Boolean manufacturerSheetOnly, Boolean realResultOnly) {
        static Filters empty() { return new Filters(null, null, null, null, null, null, null, null, null, null, null, null, false, false); }
        @JsonAnySetter
        void unknown(String name, Object ignored) { throw new IllegalArgumentException("Unknown search filter: " + name); }
    }
}
