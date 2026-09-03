package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.shared.DomainException;

/** Derived owned and available counts for one PaintProduct, not an individually identified pot. */
public record WorkshopPaintStock(String paintProductId, int quantity, int availableQuantity) {
    public WorkshopPaintStock {
        paintProductId = DomainFields.id(paintProductId, "paintProductId");
        if (availableQuantity < 0 || availableQuantity > quantity) throw new DomainException("invalid_workshop_paint_inventory", "Invalid available pot count.");
        if (quantity < 0) throw new DomainException("invalid_workshop_paint_inventory",
                "Paint quantity cannot be negative: " + paintProductId);
    }
}
