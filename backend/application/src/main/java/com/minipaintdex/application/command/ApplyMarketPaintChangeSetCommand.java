package com.minipaintdex.application.command;

import com.minipaintdex.application.document.StructuredDocument;

import java.util.List;

public record ApplyMarketPaintChangeSetCommand(
        int schemaVersion,
        String kind,
        List<Operation> operations,
        boolean dryRun,
        List<StructuredDocument> catalogEditions) {

    public ApplyMarketPaintChangeSetCommand {
        operations = operations == null ? List.of() : List.copyOf(operations);
        catalogEditions = catalogEditions == null ? List.of() : List.copyOf(catalogEditions);
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
