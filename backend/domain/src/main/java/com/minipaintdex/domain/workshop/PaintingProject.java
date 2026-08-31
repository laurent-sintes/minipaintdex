package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.workflow.DomainException;

import java.time.Instant;
import java.util.Objects;

/** Aggregate root for the owner's intent and progress when painting one market product. */
public record PaintingProject(
        String id,
        String paintableProductId,
        String name,
        PaintingProjectStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public PaintingProject {
        if (id == null || id.isBlank()) {
            throw new DomainException("invalid_painting_project", "Painting project id is required.");
        }
        if (paintableProductId == null || paintableProductId.isBlank()) {
            throw new DomainException("invalid_painting_project", "Paintable product id is required.");
        }
        if (name == null || name.isBlank()) {
            throw new DomainException("invalid_painting_project", "Painting project name is required.");
        }
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
