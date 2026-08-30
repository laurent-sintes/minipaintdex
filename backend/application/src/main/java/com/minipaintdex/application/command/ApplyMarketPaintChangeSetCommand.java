package com.minipaintdex.application.command;

import java.util.List;
import java.util.Map;

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
            Map<String, Object> record,
            int workshopQuantityDelta,
            boolean confirmedRemoval) {
        public Operation {
            record = record == null ? Map.of() : Map.copyOf(record);
        }
    }
}
