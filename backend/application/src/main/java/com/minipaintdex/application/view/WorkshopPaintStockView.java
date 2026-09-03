package com.minipaintdex.application.view;

/**
 * Workshop-owned stock composed from a market reference and the personal quantity.
 * The market reference remains immutable; ownership belongs exclusively to the workshop context.
 */
public record WorkshopPaintStockView(PaintProductView paintProduct, int quantity, int availableQuantity, String personalImage) {
    public WorkshopPaintStockView {
        if (paintProduct == null) throw new IllegalArgumentException("paintProduct is required.");
        if (quantity < 1) throw new IllegalArgumentException("quantity must be positive.");
        if (availableQuantity < 0 || availableQuantity > quantity) throw new IllegalArgumentException("Invalid available quantity.");
    }
}
