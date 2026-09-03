package com.minipaintdex.application.command;

import java.time.Instant;
import java.util.List;

public record ImportPaintPotsCommand(int schemaVersion, String kind, List<Registration> pots, boolean dryRun,
        String actorId, String correlationId, String idempotencyKey) {
    public ImportPaintPotsCommand { pots = pots == null ? List.of() : List.copyOf(pots); }
    public record Registration(String paintPotId, String paintProductId, Instant acquiredAt) {}
}
