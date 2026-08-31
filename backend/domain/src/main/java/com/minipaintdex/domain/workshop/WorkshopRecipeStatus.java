package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.shared.DomainException;

public enum WorkshopRecipeStatus {
    DRAFT("draft"),
    VALIDATED("validated"),
    ACTIVE("active"),
    SUPERSEDED("superseded"),
    ARCHIVED("archived");

    private final String id;

    WorkshopRecipeStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static WorkshopRecipeStatus fromId(String id) {
        for (var status : values()) if (status.id.equals(id)) return status;
        throw new DomainException("invalid_input", "Unknown workshop recipe status: " + id);
    }
}
