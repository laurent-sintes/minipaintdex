package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.workflow.DomainException;

import java.time.Instant;
import java.util.List;

/** Aggregate root representing the owner's durable workshop and its painting projects. */
public record Workshop(String id, List<String> paintingProjectIds, Instant updatedAt) {
    public static final String DEFAULT_ID = "my-workshop";

    public Workshop {
        if (id == null || id.isBlank()) throw new DomainException("invalid_workshop", "Workshop id is required.");
        paintingProjectIds = paintingProjectIds == null ? List.of() : List.copyOf(paintingProjectIds);
        if (paintingProjectIds.stream().distinct().count() != paintingProjectIds.size()) {
            throw new DomainException("invalid_workshop", "Duplicate painting project in workshop.");
        }
    }

    public boolean containsPaintingProject(String paintingProjectId) {
        return paintingProjectIds.contains(paintingProjectId);
    }
}
