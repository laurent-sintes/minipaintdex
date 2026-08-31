package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.workflow.DomainException;

public enum PaintingProjectStatus {
    PLANNED("planned"),
    ACTIVE("active"),
    COMPLETED("completed"),
    ARCHIVED("archived");

    private final String id;

    PaintingProjectStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static PaintingProjectStatus fromId(String id) {
        for (var status : values()) if (status.id.equals(id)) return status;
        throw new DomainException("invalid_painting_project", "Unknown painting project status: " + id);
    }
}
