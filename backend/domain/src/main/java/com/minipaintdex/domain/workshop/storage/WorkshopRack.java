package com.minipaintdex.domain.workshop.storage;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventSourcedAggregateRoot;
import com.minipaintdex.domain.shared.storage.RackRowDefinition;
import com.minipaintdex.domain.shared.storage.StorageFields;
import java.time.Instant;
import java.util.List;

public final class WorkshopRack extends EventSourcedAggregateRoot {
    private String id;
    private Configuration configuration;
    private WorkshopRack() {}
    public static WorkshopRack register(String id, Configuration configuration, Instant at) {
        var rack = new WorkshopRack(); rack.raise(new Registered(id, configuration, at)); return rack;
    }
    public static WorkshopRack rehydrate(List<? extends RackEvent> events) {
        var rack = new WorkshopRack(); rack.replayHistory(events, Registered.class, "workshop_rack"); return rack;
    }
    public void configure(Configuration value, Instant at) { raise(new Configured(id, value, at)); }
    @Override public String id() { return id; }
    public Configuration configuration() { return configuration; }
    @Override protected void apply(DomainEvent event) {
        switch (event) {
            case Registered value -> { id = value.workshopRackId(); configuration = value.configuration(); }
            case Configured value -> configuration = value.configuration();
            default -> throw StorageFields.invalid("Unsupported rack event.");
        }
    }
    public record Configuration(String rackProductId, String name, String location, boolean owned, List<RackRowDefinition> rowOverrides) {
        public Configuration {
            if (rackProductId != null) rackProductId = StorageFields.id(rackProductId);
            name = StorageFields.text(name, "name"); location = location == null ? "" : location;
            rowOverrides = rowOverrides == null ? List.of() : List.copyOf(rowOverrides);
            if (rackProductId == null || !rowOverrides.isEmpty()) rowOverrides = RackRowDefinition.validateRows(rowOverrides);
        }
    }
    public sealed interface RackEvent extends DomainEvent {
        String workshopRackId();
        @Override default String aggregateId() { return workshopRackId(); }
        @Override default String aggregateType() { return "workshop_rack"; }
        @Override default String scopePaintingProjectId() { return null; }
    }
    public record Registered(String workshopRackId, Configuration configuration, Instant occurredAt) implements RackEvent {
        public Registered { workshopRackId = StorageFields.id(workshopRackId); java.util.Objects.requireNonNull(configuration); java.util.Objects.requireNonNull(occurredAt); }
        @Override public String eventType() { return "workshop_rack.registered"; }
    }
    public record Configured(String workshopRackId, Configuration configuration, Instant occurredAt) implements RackEvent {
        public Configured { workshopRackId = StorageFields.id(workshopRackId); java.util.Objects.requireNonNull(configuration); java.util.Objects.requireNonNull(occurredAt); }
        @Override public String eventType() { return "workshop_rack.configured"; }
    }
}
