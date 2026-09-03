package com.minipaintdex.cli;

import com.minipaintdex.application.query.*;
import picocli.CommandLine;
import picocli.CommandLine.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

@Command(name = "paint-usage-guides", mixinStandardHelpOptions = true,
        subcommands = {PaintUsageGuidesCli.ListGuides.class, PaintUsageGuidesCli.Show.class})
final class PaintUsageGuidesCli implements Runnable {
    @ParentCommand MiniPaintDexCli.Market parent;
    public void run() { CommandLine.usage(this, System.out); }
    @Command(name = "list", mixinStandardHelpOptions = true)
    static final class ListGuides implements Callable<Integer> {
        @ParentCommand PaintUsageGuidesCli parent;
        @Option(names = "--brand") String brand;
        @Option(names = "--range") String range;
        @Option(names = "--paint-product-id") String paintProductId;
        @Option(names = "--language", defaultValue = "fr") String language;
        @Option(names = "--page", defaultValue = "0") int page;
        @Option(names = "--size", defaultValue = "50") int size;
        @Option(names = "--sort", defaultValue = "id,asc") String sort;
        @Option(names = "--correlation-id") String correlationId;
        public Integer call() {
            var pieces = sort.split(",");
            if (pieces.length != 2 || !List.of("asc", "desc").contains(pieces[1])) throw new com.minipaintdex.domain.shared.DomainException("invalid_input", "Sort must be field,asc or field,desc");
            var root = parent.parent.root;
            root.output(root.market().searchPaintUsageGuides(new SearchPaintUsageGuidesQuery(brand, range, paintProductId, language,
                    new PageQuery(page, size, List.of(new SortOrder(pieces[0], pieces[1].equals("asc") ? SortOrder.Direction.ASCENDING : SortOrder.Direction.DESCENDING))),
                    correlationId == null ? UUID.randomUUID().toString() : correlationId)));
            return 0;
        }
    }
    @Command(name = "show", mixinStandardHelpOptions = true)
    static final class Show implements Callable<Integer> {
        @ParentCommand PaintUsageGuidesCli parent;
        @Option(names = "--paint-usage-guide-id", required = true) String paintUsageGuideId;
        @Option(names = "--language", defaultValue = "fr") String language;
        @Option(names = "--correlation-id") String correlationId;
        public Integer call() {
            var root = parent.parent.root;
            root.output(root.market().getPaintUsageGuide(new GetPaintUsageGuideQuery(paintUsageGuideId, language,
                    correlationId == null ? UUID.randomUUID().toString() : correlationId)));
            return 0;
        }
    }
}
