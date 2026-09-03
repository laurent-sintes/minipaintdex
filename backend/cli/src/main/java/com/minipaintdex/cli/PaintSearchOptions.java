package com.minipaintdex.cli;

import com.minipaintdex.application.query.*;
import picocli.CommandLine.Option;
import picocli.CommandLine.Mixin;
import java.util.List;
import java.util.Set;

final class PaintSearchOptions {
    @Mixin PaintFilterOptions filters;
    @Option(names = "--include", split = ",", defaultValue = "results", description = "results, suggestions, or results,suggestions") Set<String> include;
    @Option(names = "--page", defaultValue = "0") int page;
    @Option(names = "--size", defaultValue = "60") int size;
    @Option(names = "--sort", description = "Repeatable field,asc or field,desc") List<String> sort;
    @Option(names = "--suggestion-limit") Integer suggestionLimit;
    @Option(names = "--correlation-id") String correlationId;
    PaintSearchQuery query() {
        var orders = (sort == null ? List.<String>of() : sort).stream().map(value -> {
            var parts = value.split(",", -1);
            if (parts.length > 2 || parts[0].isBlank()
                    || (parts.length == 2 && !List.of("asc", "desc").contains(parts[1])))
                throw new IllegalArgumentException("Sort must be field,asc or field,desc.");
            return new SortOrder(parts[0], parts.length == 2 && parts[1].equals("desc")
                    ? SortOrder.Direction.DESCENDING : SortOrder.Direction.ASCENDING);
        }).toList();
        return new PaintSearchQuery(filters.query(), filters.manufacturerSheetOnly, filters.realResultOnly,
                include, new PageQuery(page, size, orders), suggestionLimit,
                correlationId == null || correlationId.isBlank() ? java.util.UUID.randomUUID().toString() : correlationId);
    }
}
