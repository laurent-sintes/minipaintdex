package com.minipaintdex.application.view;

/** Compact reference candidate; no pot identity, stock count, history or source envelopes. */
public record PaintProductSuggestion(String paintProductId, String name, String brand, String range,
        String reference, String manufacturerImage, String colorHex) {
    public static PaintProductSuggestion from(PaintProductView paint) {
        return new PaintProductSuggestion(paint.id(), paint.name(), paint.brand(), paint.range(),
                paint.reference(), paint.manufacturerImage(), paint.colorHex());
    }
}
