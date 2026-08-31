package com.minipaintdex.application.view;

import java.util.List;

/** Calculated or explicitly planned paint purchase. */
public record ShoppingItemView(
        String id,
        String brand,
        String name,
        String reference,
        String colorHex,
        String reason,
        String priority,
        String kind,
        boolean planned,
        String marketPaintId,
        List<String> sourceProductIds,
        List<String> sourceProductNames,
        boolean checked) {
    public ShoppingItemView {
        sourceProductIds = List.copyOf(sourceProductIds);
        sourceProductNames = List.copyOf(sourceProductNames);
    }
}
