package com.minipaintdex.application.command;

import java.util.List;

public record ReplaceWorkshopPaintInventoryCommand(
        int schemaVersion,
        String kind,
        List<Entry> paints,
        boolean dryRun) {

    public ReplaceWorkshopPaintInventoryCommand {
        paints = paints == null ? List.of() : List.copyOf(paints);
    }

    public record Entry(String paintId, int quantity) {}
}
