package com.minipaintdex.application.view;

/**
 * Workshop-owned stock composed from a market reference and the personal quantity.
 * The market reference remains immutable; ownership belongs exclusively to the workshop context.
 */
public record WorkshopPaintView(MarketPaintView marketPaint, int quantity) {
    public WorkshopPaintView {
        if (marketPaint == null) throw new IllegalArgumentException("marketPaint is required.");
        if (quantity < 1) throw new IllegalArgumentException("quantity must be positive.");
    }
}
