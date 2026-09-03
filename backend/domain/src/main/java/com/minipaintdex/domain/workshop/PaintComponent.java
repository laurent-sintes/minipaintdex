package com.minipaintdex.domain.workshop;

public record PaintComponent(String paintProductId, double proportion, String role) {
    public PaintComponent {
        paintProductId = DomainFields.id(paintProductId, "paintProductId");
        if (!Double.isFinite(proportion) || proportion <= 0) {
            throw DomainFields.invalid("proportion must be a positive finite number.");
        }
        role = DomainFields.optional(role);
    }
}
