package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.shared.DomainException;

import java.util.HashSet;
import java.util.List;

/** Explicit owner purchase intentions; calculated missing paints remain projections. */
public record WorkshopShoppingPlan(List<PaintPurchaseIntent> intents) {
    public WorkshopShoppingPlan {
        intents = intents == null ? List.of() : List.copyOf(intents);
        var ids = new HashSet<String>();
        for (var intent : intents) {
            if (intent == null) throw invalid("Shopping plan cannot contain null entries.");
            if (!ids.add(intent.id())) throw invalid("Duplicate shopping intent: " + intent.id());
        }
    }

    public record PaintPurchaseIntent(
            String id,
            String paintProductId,
            String brand,
            String name,
            String reference,
            String colorHex,
            String reason,
            Priority priority) {
        public PaintPurchaseIntent {
            id = DomainFields.id(id, "shoppingIntentId");
            paintProductId = optionalId(paintProductId);
            brand = optional(brand);
            name = optional(name);
            reference = optional(reference);
            colorHex = optional(colorHex);
            reason = optional(reason);
            priority = priority == null ? Priority.LOW : priority;
            if (paintProductId == null && (brand == null || name == null)) {
                throw invalid("A shopping intent without a Market paint requires brand and name.");
            }
            if (colorHex != null && !colorHex.matches("#[0-9A-Fa-f]{6}")) {
                throw invalid("Shopping colorHex must use #RRGGBB.");
            }
        }
    }

    public enum Priority {
        LOW("low"), MEDIUM("medium"), HIGH("high");

        private final String id;
        Priority(String id) { this.id = id; }
        public String id() { return id; }
        public static Priority fromId(String id) {
            for (var value : values()) if (value.id.equals(id)) return value;
            throw invalid("Unknown shopping priority: " + id);
        }
    }

    private static String optionalId(String value) {
        return value == null || value.isBlank() ? null : DomainFields.id(value, "paintProductId");
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static DomainException invalid(String message) {
        return new DomainException("invalid_workshop_shopping_plan", message);
    }
}
