package com.minipaintdex.server.api;
import com.minipaintdex.application.query.*;
import com.minipaintdex.application.result.PageResult;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
final class RackApiSupport {
    private RackApiSupport() {}
    static String correlation(String value) { return value == null || value.isBlank() ? java.util.UUID.randomUUID().toString() : value; }
    static PageQuery page(Pageable page) { return new PageQuery(page.getPageNumber(), page.getPageSize(), page.getSort().stream()
            .map(order -> new SortOrder(order.getProperty(), order.isAscending() ? SortOrder.Direction.ASCENDING : SortOrder.Direction.DESCENDING)).toList()); }
    static <T> EntityModel<T> pages(T value, PageResult<?> page) {
        var model = EntityModel.of(value, link(page.page(), page.size()).withSelfRel(), link(0, page.size()).withRel("first"),
                link(Math.max(0, page.totalPages() - 1), page.size()).withRel("last"));
        if (page.hasPrevious()) model.add(link(page.page() - 1, page.size()).withRel("previous"));
        if (page.hasNext()) model.add(link(page.page() + 1, page.size()).withRel("next"));
        return model;
    }
    private static Link link(int page, int size) {
        return Link.of(ServletUriComponentsBuilder.fromCurrentRequest().replaceQueryParam("page", page).replaceQueryParam("size", size).build().toUriString());
    }
}
