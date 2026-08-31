package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventSourcedAggregateRoot;
import com.minipaintdex.domain.shared.DomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Aggregate root representing the owner's durable workshop and its painting projects. */
public final class Workshop extends EventSourcedAggregateRoot {
    public static final String DEFAULT_ID = "my-workshop";

    private String id;
    private String name;
    private final List<String> paintingProjectIds = new ArrayList<>();
    private Instant updatedAt;

    private Workshop() {}

    public static Workshop create(String id, String name, Instant occurredAt) {
        if (!DEFAULT_ID.equals(id)) {
            throw new DomainException("invalid_workshop_id", "Workshop id must be " + DEFAULT_ID + ".");
        }
        var workshop = new Workshop();
        workshop.raise(new WorkshopCreated(id, name, occurredAt));
        return workshop;
    }

    public static Workshop rehydrate(List<? extends WorkshopEvent> history) {
        var workshop = new Workshop();
        workshop.replayHistory(history, WorkshopCreated.class, "workshop");
        return workshop;
    }

    public void registerPaintingProject(String paintingProjectId, Instant occurredAt) {
        raise(new PaintingProjectRegistered(id, paintingProjectId, occurredAt));
    }

    @Override
    protected void apply(DomainEvent event) {
        switch (event) {
            case WorkshopCreated created -> {
                if (!DEFAULT_ID.equals(created.workshopId())) {
                    throw new DomainException("invalid_workshop_id", "Workshop id must be " + DEFAULT_ID + ".");
                }
                id = created.workshopId();
                name = created.name();
                updatedAt = created.occurredAt();
            }
            case PaintingProjectRegistered registered -> {
                if (paintingProjectIds.contains(registered.paintingProjectId())) {
                    throw new DomainException("painting_project_already_registered",
                            "Painting project is already registered in the workshop: "
                                    + registered.paintingProjectId());
                }
                paintingProjectIds.add(registered.paintingProjectId());
                updatedAt = registered.occurredAt();
            }
            default -> throw unsupported(event);
        }
    }

    @Override public String id() { return id; }
    public String name() { return name; }
    public List<String> paintingProjectIds() { return List.copyOf(paintingProjectIds); }
    public Instant updatedAt() { return updatedAt; }

    public boolean containsPaintingProject(String paintingProjectId) {
        return paintingProjectIds.contains(paintingProjectId);
    }

    private DomainException unsupported(DomainEvent event) {
        return new DomainException("invalid_workshop_event", "Unsupported workshop event: " + event.eventType());
    }
}
