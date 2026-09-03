package com.minipaintdex.server.api;

import com.minipaintdex.application.result.PaintSearchResult;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.view.PaintProductSuggestion;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.mediatype.Affordances;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.List;

record PaintSearchResponse<T>(PageResult<T> results, List<EntityModel<PaintProductSuggestion>> suggestions, String correlationId) {
    static <T> EntityModel<PaintSearchResponse<T>> from(PaintSearchResult<T> result, boolean workshop) {
        var suggestions = result.suggestions() == null ? null : result.suggestions().stream().map(suggestion -> {
            var model = EntityModel.of(suggestion, Link.of("/api/v1/market/paint-products/" + suggestion.paintProductId()).withRel("paint-product"));
            if (workshop) model.add(Link.of("/api/v1/workshop/paint-pots?paintProductId=" + suggestion.paintProductId()).withRel("paint-pots"));
            return model;
        }).toList();
        var model = EntityModel.of(new PaintSearchResponse<>(result.results(), suggestions, result.correlationId()),
                postLink(ServletUriComponentsBuilder.fromCurrentRequest().build().toUriString()).withSelfRel());
        var page = result.results();
        if (page != null) {
            model.add(pageLink(0, page.size()).withRel("first"));
            if (page.hasPrevious()) model.add(pageLink(page.page() - 1, page.size()).withRel("previous"));
            if (page.hasNext()) model.add(pageLink(page.page() + 1, page.size()).withRel("next"));
            model.add(pageLink(Math.max(0, page.totalPages() - 1), page.size()).withRel("last"));
        }
        var collection = workshop ? "/api/v1/workshop/paint-stocks" : "/api/v1/market/paint-products";
        model.add(Link.of(collection + "/facets").withRel("facets"));
        return model;
    }

    private static Link pageLink(int page, int size) {
        return postLink(ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page).replaceQueryParam("size", size).build().toUriString());
    }

    static Link postLink(String url) {
        return Affordances.of(Link.of(url)).afford(HttpMethod.POST).withInput(PaintSearchRequest.class).toLink();
    }
}
