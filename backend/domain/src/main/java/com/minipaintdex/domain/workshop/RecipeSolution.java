package com.minipaintdex.domain.workshop;

import java.util.List;
import java.util.Objects;

public record RecipeSolution(
        RecipeSolutionType type,
        String guideSlotId,
        String paintId,
        List<PaintComponent> components,
        String instructions) {
    public RecipeSolution {
        type = Objects.requireNonNull(type, "type is required.");
        guideSlotId = DomainFields.optional(guideSlotId);
        paintId = DomainFields.optional(paintId);
        components = components == null ? List.of() : List.copyOf(components);
        instructions = DomainFields.optional(instructions);
        switch (type) {
            case SINGLE_PAINT -> {
                if (paintId == null) throw DomainFields.invalid("single_paint requires paintId.");
                if (!components.isEmpty()) throw DomainFields.invalid("single_paint cannot contain components.");
            }
            case MIXTURE, LAYER_STACK -> {
                if (components.isEmpty()) throw DomainFields.invalid(type.id() + " requires components.");
                if (paintId != null) throw DomainFields.invalid(type.id() + " uses components instead of paintId.");
            }
            case TECHNIQUE -> {
                if (instructions == null) throw DomainFields.invalid("technique requires instructions.");
            }
        }
    }

    public List<String> referencedPaintIds() {
        if (paintId != null) return List.of(paintId);
        return components.stream().map(PaintComponent::paintId).distinct().toList();
    }
}
