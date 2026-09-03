package com.minipaintdex.domain.workshop;

import java.util.List;
import java.util.Objects;

public record RecipeSolution(
        RecipeSolutionType type,
        String guideSlotId,
        String paintProductId,
        List<PaintComponent> components,
        String instructions) {
    public RecipeSolution {
        type = Objects.requireNonNull(type, "type is required.");
        guideSlotId = DomainFields.optional(guideSlotId);
        paintProductId = DomainFields.optional(paintProductId);
        components = components == null ? List.of() : List.copyOf(components);
        instructions = DomainFields.optional(instructions);
        switch (type) {
            case SINGLE_PAINT -> {
                if (paintProductId == null) throw DomainFields.invalid("single_paint requires paintProductId.");
                if (!components.isEmpty()) throw DomainFields.invalid("single_paint cannot contain components.");
            }
            case MIXTURE, LAYER_STACK -> {
                if (components.isEmpty()) throw DomainFields.invalid(type.id() + " requires components.");
                if (paintProductId != null) throw DomainFields.invalid(type.id() + " uses components instead of paintProductId.");
            }
            case TECHNIQUE -> {
                if (instructions == null) throw DomainFields.invalid("technique requires instructions.");
                if (paintProductId != null) throw DomainFields.invalid("technique uses optional components instead of paintProductId.");
            }
        }
        if (type == RecipeSolutionType.MIXTURE
                && components.stream().map(PaintComponent::paintProductId).distinct().count() != components.size()) {
            throw DomainFields.invalid("mixture cannot contain the same paint more than once.");
        }
    }

    public List<String> referencedPaintIds() {
        if (paintProductId != null) return List.of(paintProductId);
        return components.stream().map(PaintComponent::paintProductId).distinct().toList();
    }
}
