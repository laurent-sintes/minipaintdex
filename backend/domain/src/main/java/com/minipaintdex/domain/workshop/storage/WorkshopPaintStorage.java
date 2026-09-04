package com.minipaintdex.domain.workshop.storage;

import com.minipaintdex.domain.event.*;
import com.minipaintdex.domain.shared.storage.StorageFields;
import java.time.Instant;
import java.util.List;

public final class WorkshopPaintStorage extends EventSourcedAggregateRoot {
    public static final String ID = "my-workshop-paint-storage";
    private List<PaintPotPlacement> placements = List.of();
    public static WorkshopPaintStorage rehydrate(List<ArrangementRecorded> events) {
        var storage = new WorkshopPaintStorage();
        events.forEach(storage::replay);
        return storage;
    }
    @Override public String id() { return ID; }
    public List<PaintPotPlacement> placements() { return placements; }
    public void arrange(List<PaintPotPlacement> next, List<StorageCompatibility.Pot> pots, List<StorageCompatibility.Rack> racks,
            PaintStoragePolicy policy, boolean allowEstimates, Instant at) {
        StorageCompatibility.validate(next, pots, racks, policy, allowEstimates);
        raise(new ArrangementRecorded(ID, next, allowEstimates, at));
    }
    public void removePot(String paintPotId, Instant at) {
        var next = placements.stream().filter(value -> !value.paintPotId().equals(paintPotId)).toList();
        if (!next.equals(placements)) raise(new ArrangementRecorded(ID, next, true, at));
    }
    @Override protected void apply(DomainEvent event) {
        if (!(event instanceof ArrangementRecorded value)) throw StorageFields.invalid("Unsupported storage event.");
        placements = value.placements();
    }
    public record ArrangementRecorded(String workshopPaintStorageId, List<PaintPotPlacement> placements,
            boolean estimatesAccepted, Instant occurredAt) implements DomainEvent {
        public ArrangementRecorded {
            if (!ID.equals(workshopPaintStorageId)) throw StorageFields.invalid("Invalid paint storage identity.");
            placements = List.copyOf(placements); java.util.Objects.requireNonNull(occurredAt);
            if (placements.stream().map(PaintPotPlacement::paintPotId).distinct().count() != placements.size()) throw StorageFields.invalid("Duplicate pot placement.");
        }
        @Override public String aggregateId() { return workshopPaintStorageId; }
        @Override public String aggregateType() { return "workshop_paint_storage"; }
        @Override public String eventType() { return "workshop_paint_storage.arrangement_recorded"; }
        @Override public String scopePaintingProjectId() { return null; }
    }
}
