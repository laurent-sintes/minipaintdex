package com.minipaintdex.application.view;

import java.util.List;

/** Shopping presentation joining a calculated requirement and/or an explicit purchase intent. */
public record ShoppingListEntryView(
        String id,
        String brand,
        String name,
        String reference,
        String colorHex,
        String reason,
        String priority,
        String kind,
        boolean planned,
        String paintProductId,
        List<String> sourcePaintableProductIds,
        List<String> sourcePaintableProductNames,
        boolean checked) {
    public ShoppingListEntryView {
        sourcePaintableProductIds = List.copyOf(sourcePaintableProductIds);
        sourcePaintableProductNames = List.copyOf(sourcePaintableProductNames);
    }
}
