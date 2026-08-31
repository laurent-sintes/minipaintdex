package com.minipaintdex.application.usecase;

import com.minipaintdex.application.command.ApplyMarketPaintChangeSetCommand;
import com.minipaintdex.application.command.ApplyMarketPaintableProductChangeSetCommand;
import com.minipaintdex.application.command.ReplaceWorkshopPaintInventoryCommand;
import com.minipaintdex.application.result.ApplyMarketPaintChangeSetResult;
import com.minipaintdex.application.result.ApplyMarketPaintableProductChangeSetResult;
import com.minipaintdex.application.result.ReplaceWorkshopPaintInventoryResult;
import com.minipaintdex.application.view.RebuildProjectionResult;

import java.util.Map;

/** Deterministic administration use cases shared by REST and Picocli adapters. */
public interface AdministrationUseCases {
    /** Validates a versioned market-paint change set and applies it atomically unless dry-run. */
    ApplyMarketPaintChangeSetResult applyMarketPaintChangeSet(ApplyMarketPaintChangeSetCommand command);
    /** Validates and atomically replaces one paintable product with its sourced guides unless dry-run. */
    ApplyMarketPaintableProductChangeSetResult applyMarketPaintableProductChangeSet(ApplyMarketPaintableProductChangeSetCommand command);
    /** Validates paint identifiers and replaces the personal inventory unless dry-run. */
    ReplaceWorkshopPaintInventoryResult replaceWorkshopPaintInventory(ReplaceWorkshopPaintInventoryCommand command);
    /** Recomputes projections from the authoritative ledger without manufacturing events. */
    RebuildProjectionResult rebuildProjections();
}
