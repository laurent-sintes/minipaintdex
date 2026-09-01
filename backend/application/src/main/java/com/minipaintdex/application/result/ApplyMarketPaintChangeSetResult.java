package com.minipaintdex.application.result;

public record ApplyMarketPaintChangeSetResult(
        int added,
        int updated,
        int rekeyed,
        int retired,
        int deleted,
        int unchanged,
        int workshopInventoryChanged,
        int total,
        boolean applied) {
}
