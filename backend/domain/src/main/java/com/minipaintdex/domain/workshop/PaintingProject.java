package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventSourcedAggregateRoot;
import com.minipaintdex.domain.shared.DomainException;

import java.time.Instant;
import java.util.List;

/** Aggregate root for the owner's intent and progress when painting one market product. */
public final class PaintingProject extends EventSourcedAggregateRoot {
    private String id;
    private String workshopId;
    private String paintableProductId;
    private String name;
    private int paintableCount;
    private PaintingProjectStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private PaintingProject() {}

    public static PaintingProject create(
            String id, String workshopId, String paintableProductId, String name,
            int paintableCount, Instant occurredAt) {
        var project = new PaintingProject();
        project.raise(new PaintingProjectCreated(
                id, workshopId, paintableProductId, name, paintableCount, occurredAt));
        return project;
    }

    public static PaintingProject rehydrate(List<? extends PaintingProjectEvent> history) {
        var project = new PaintingProject();
        project.replayHistory(history, PaintingProjectCreated.class, "painting_project");
        return project;
    }

    public void changeStatus(PaintingProjectStatus target, Instant occurredAt) {
        if (target == status) return;
        raise(new PaintingProjectStatusChanged(id, target, occurredAt));
    }

    @Override
    protected void apply(DomainEvent event) {
        switch (event) {
            case PaintingProjectCreated created -> {
                id = created.paintingProjectId();
                workshopId = created.workshopId();
                paintableProductId = created.paintableProductId();
                name = created.name();
                paintableCount = created.paintableCount();
                status = PaintingProjectStatus.PLANNED;
                createdAt = created.occurredAt();
                updatedAt = created.occurredAt();
            }
            case PaintingProjectStatusChanged changed -> {
                assertStatusTransition(changed.status());
                status = changed.status();
                updatedAt = changed.occurredAt();
            }
            default -> throw new DomainException("invalid_painting_project_event",
                    "Unsupported painting project event: " + event.eventType());
        }
    }

    private void assertStatusTransition(PaintingProjectStatus target) {
        if (target == null) throw new DomainException("invalid_painting_project_transition", "Target status is required.");
        var allowed = switch (status) {
            case PLANNED -> target == PaintingProjectStatus.ACTIVE || target == PaintingProjectStatus.ARCHIVED;
            case ACTIVE -> target == PaintingProjectStatus.COMPLETED || target == PaintingProjectStatus.ARCHIVED;
            case COMPLETED -> target == PaintingProjectStatus.ACTIVE || target == PaintingProjectStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
        if (!allowed) {
            throw new DomainException("invalid_painting_project_transition",
                    "Cannot change painting project from " + status.id() + " to " + target.id() + ".");
        }
    }

    @Override public String id() { return id; }
    public String workshopId() { return workshopId; }
    public String paintableProductId() { return paintableProductId; }
    public String name() { return name; }
    public int paintableCount() { return paintableCount; }
    public PaintingProjectStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
