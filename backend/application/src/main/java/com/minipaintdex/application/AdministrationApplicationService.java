package com.minipaintdex.application;

import com.minipaintdex.application.command.ApplyMarketPaintChangeSetCommand;
import com.minipaintdex.application.command.ApplyMarketPaintableProductChangeSetCommand;
import com.minipaintdex.application.command.ReplaceWorkshopPaintInventoryCommand;
import com.minipaintdex.application.result.ApplyMarketPaintChangeSetResult;
import com.minipaintdex.application.result.ApplyMarketPaintableProductChangeSetResult;
import com.minipaintdex.application.result.ReplaceWorkshopPaintInventoryResult;
import com.minipaintdex.application.usecase.AdministrationUseCases;
import com.minipaintdex.application.view.RebuildProjectionResult;

import java.util.Objects;

/** Cohesive administrative service for deterministic bulk imports and projection maintenance. */
public final class AdministrationApplicationService implements AdministrationUseCases {
    private final MiniPaintDexService kernel;

    public AdministrationApplicationService(MiniPaintDexService kernel) {
        this.kernel = Objects.requireNonNull(kernel);
    }

    @Override public ApplyMarketPaintChangeSetResult applyMarketPaintChangeSet(
            ApplyMarketPaintChangeSetCommand command) {
        return kernel.applyMarketPaintChangeSet(command);
    }
    @Override public ApplyMarketPaintableProductChangeSetResult applyMarketPaintableProductChangeSet(
            ApplyMarketPaintableProductChangeSetCommand command) {
        return kernel.applyMarketPaintableProductChangeSet(command);
    }
    @Override public ReplaceWorkshopPaintInventoryResult replaceWorkshopPaintInventory(
            ReplaceWorkshopPaintInventoryCommand command) {
        return kernel.replaceWorkshopPaintInventory(command);
    }
    @Override public RebuildProjectionResult rebuildProjections() { return kernel.rebuildProjections(); }
}
