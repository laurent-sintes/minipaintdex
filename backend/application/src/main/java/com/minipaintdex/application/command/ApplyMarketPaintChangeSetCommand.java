package com.minipaintdex.application.command;

import com.minipaintdex.application.document.StructuredDocument;

import java.util.List;

public record ApplyMarketPaintChangeSetCommand(
        int schemaVersion,
        String kind,
        List<Operation> operations,
        boolean dryRun) {

    public ApplyMarketPaintChangeSetCommand {
        operations = operations == null ? List.of() : List.copyOf(operations);
    }

    public record Operation(
            String action,
            StructuredDocument record,
            int workshopQuantityDelta,
            boolean confirmedRemoval) {
        public Operation {
            record = record == null ? new StructuredDocument(List.of()) : record;
        }
    }
}
