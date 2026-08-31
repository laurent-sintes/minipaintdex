package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.shared.DomainException;

public enum RecipeSolutionType {
    SINGLE_PAINT("single_paint"),
    MIXTURE("mixture"),
    LAYER_STACK("layer_stack"),
    TECHNIQUE("technique");

    private final String id;

    RecipeSolutionType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static RecipeSolutionType fromId(String id) {
        for (var type : values()) if (type.id.equals(id)) return type;
        throw new DomainException("invalid_recipe_solution", "Unknown recipe solution type: " + id);
    }
}
