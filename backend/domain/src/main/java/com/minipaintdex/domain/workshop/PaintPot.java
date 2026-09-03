package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.event.EventSourcedAggregateRoot;
import com.minipaintdex.domain.workshop.PaintPotEvent.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PaintPot extends EventSourcedAggregateRoot {
    private String id;
    private String paintProductId;
    private Instant acquiredAt;
    private Instant registeredAt;
    private Instant openedAt;
    private PaintPotCondition condition = PaintPotCondition.UNKNOWN;
    private PaintPotRemainingLevel remainingLevel = PaintPotRemainingLevel.UNKNOWN;
    private PaintPotPossession possession = PaintPotPossession.OWNED;
    private final List<PaintPotPhotoAdded> photos = new ArrayList<>();
    private final List<PaintPotNoteAdded> notes = new ArrayList<>();

    private PaintPot() {}
    public static PaintPot register(String id, String paintProductId, Instant acquiredAt, Instant at) {
        var pot = new PaintPot();
        pot.raise(new PaintPotRegistered(id, paintProductId, acquiredAt, at));
        return pot;
    }
    public static PaintPot rehydrate(List<PaintPotEvent> history) {
        var pot = new PaintPot();
        pot.replayHistory(history, PaintPotRegistered.class, "paint_pot");
        return pot;
    }
    public void observe(PaintPotCondition condition, PaintPotRemainingLevel remainingLevel, Instant at) {
        requireOwned();
        raise(new PaintPotObserved(id, condition, remainingLevel, at));
    }
    public void open(Instant at) {
        requireOwned();
        if (openedAt != null) throw DomainFields.invalid("Pot opening is already recorded.");
        if (acquiredAt != null && at.isBefore(acquiredAt)) throw DomainFields.invalid("Opening precedes acquisition.");
        raise(new PaintPotOpened(id, at));
    }
    public void changePossession(PaintPotPossession value, Instant at) {
        if (value == possession) throw DomainFields.invalid("Pot possession is unchanged.");
        raise(new PaintPotPossessionChanged(id, value, at));
    }
    public void addNote(String note, Instant at) { raise(new PaintPotNoteAdded(id, note, at)); }
    public void addPhoto(String mediaId, String url, String caption, String filename, String type, long size, String hash, Instant at) {
        raise(new PaintPotPhotoAdded(id, mediaId, url, caption, filename, type, size, hash, at));
    }
    private void requireOwned() {
        if (possession != PaintPotPossession.OWNED) throw DomainFields.invalid("The pot is no longer owned.");
    }
    @Override protected void apply(DomainEvent event) {
        switch (event) {
            case PaintPotRegistered value -> {
                id = value.paintPotId(); paintProductId = value.paintProductId();
                acquiredAt = value.acquiredAt(); registeredAt = value.occurredAt();
            }
            case PaintPotObserved value -> { requireOwned(); condition = value.condition(); remainingLevel = value.remainingLevel(); }
            case PaintPotOpened value -> {
                requireOwned();
                if (openedAt != null || (acquiredAt != null && value.occurredAt().isBefore(acquiredAt))) throw DomainFields.invalid("Invalid pot opening.");
                openedAt = value.occurredAt();
            }
            case PaintPotPossessionChanged value -> possession = value.possession();
            case PaintPotNoteAdded value -> notes.add(value);
            case PaintPotPhotoAdded value -> photos.add(value);
            default -> throw DomainFields.invalid("Unsupported paint pot event.");
        }
    }
    @Override public String id() { return id; }
    public String paintProductId() { return paintProductId; }
    public Instant acquiredAt() { return acquiredAt; }
    public Instant registeredAt() { return registeredAt; }
    public Instant openedAt() { return openedAt; }
    public PaintPotCondition condition() { return condition; }
    public PaintPotRemainingLevel remainingLevel() { return remainingLevel; }
    public PaintPotPossession possession() { return possession; }
    public List<PaintPotPhotoAdded> photos() { return List.copyOf(photos); }
    public List<PaintPotNoteAdded> notes() { return List.copyOf(notes); }
    public List<String> allowedActions() {
        var actions = new ArrayList<>(List.of("add-note", "add-photo", "change-possession"));
        if (possession == PaintPotPossession.OWNED) {
            actions.add("observe");
            if (openedAt == null) actions.add("open");
        }
        return List.copyOf(actions);
    }
    public boolean available() {
        return possession == PaintPotPossession.OWNED && condition != PaintPotCondition.DRIED && remainingLevel != PaintPotRemainingLevel.EMPTY;
    }
}
