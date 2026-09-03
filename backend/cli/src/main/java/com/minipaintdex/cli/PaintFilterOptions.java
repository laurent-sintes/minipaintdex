package com.minipaintdex.cli;

import com.minipaintdex.application.query.SearchPaintProductsQuery;
import picocli.CommandLine.Option;
import java.util.List;

final class PaintFilterOptions {
    @Option(names = "--query") String query;
    @Option(names = "--brand") List<String> brand;
    @Option(names = "--range", description = "Repeatable brand::range; OR with brands") List<String> range;
    @Option(names = "--role") List<String> role;
    @Option(names = "--application-method") List<String> applicationMethod;
    @Option(names = "--application-system") List<String> applicationSystem;
    @Option(names = "--color") List<String> color;
    @Option(names = "--finish") List<String> finish;
    @Option(names = "--medium") List<String> medium;
    @Option(names = "--coverage") List<String> coverage;
    @Option(names = "--effect") List<String> effect;
    @Option(names = "--undercoat") List<String> undercoat;
    @Option(names = "--lifecycle") List<String> lifecycle;
    @Option(names = "--manufacturer-sheet-only") boolean manufacturerSheetOnly;
    @Option(names = "--real-result-only") boolean realResultOnly;
    SearchPaintProductsQuery query() {
        return SearchPaintProductsQuery.fromSelections(query, brand, range, role, applicationMethod,
                applicationSystem, color, finish, medium, coverage, effect, undercoat, lifecycle);
    }
}
