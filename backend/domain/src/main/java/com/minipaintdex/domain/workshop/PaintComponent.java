package com.minipaintdex.domain.workshop;

public record PaintComponent(String paintId, double proportion, String role) {
    public PaintComponent {
        paintId = DomainFields.required(paintId, "paintId");
        if (!Double.isFinite(proportion) || proportion <= 0) {
            throw DomainFields.invalid("proportion must be a positive finite number.");
        }
        role = DomainFields.optional(role);
    }
}
