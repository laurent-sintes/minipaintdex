package com.minipaintdex.application.result;

public record ReplaceWorkshopPaintInventoryResult(
        int paintCount,
        int totalQuantity,
        boolean applied) {
}
