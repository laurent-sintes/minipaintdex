package com.minipaintdex.application.command;

import com.minipaintdex.application.document.StructuredDocument;

import java.util.List;

public record ApplyPaintProductChangeSetCommand(
        int schemaVersion,
        String kind,
        List<Operation> operations,
        boolean dryRun,
        List<StructuredDocument> catalogEditions, List<StructuredDocument> paintUsageGuides,
        List<StructuredDocument> containerFormats) {

    public ApplyPaintProductChangeSetCommand {
        containerFormats = containerFormats == null ? List.of() : List.copyOf(containerFormats);
        paintUsageGuides = paintUsageGuides == null ? List.of() : List.copyOf(paintUsageGuides);
        operations = operations == null ? List.of() : List.copyOf(operations);
        catalogEditions = catalogEditions == null ? List.of() : List.copyOf(catalogEditions);
    }
    public ApplyPaintProductChangeSetCommand(int schemaVersion, String kind, List<Operation> operations, boolean dryRun,
            List<StructuredDocument> catalogEditions, List<StructuredDocument> paintUsageGuides) {
        this(schemaVersion, kind, operations, dryRun, catalogEditions, paintUsageGuides, List.of());
    }

    public record Operation(
            String action,
            String previousId,
            StructuredDocument record,
            int workshopQuantityDelta,
            boolean confirmedRemoval) {
        public Operation {
            record = record == null ? new StructuredDocument(List.of()) : record;
        }
    }
}
