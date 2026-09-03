package com.minipaintdex.application.view;

import com.minipaintdex.domain.workshop.PaintPot;
import java.time.Instant;
import java.util.List;

public record PaintPotView(String paintPotId, String paintProductId, long version, PaintProductView paintProduct,
        String condition, String remainingLevel, String possession, boolean available,
        Instant registeredAt, Instant acquiredAt, Instant openedAt, List<Photo> photos, List<Note> notes, List<String> allowedActions) {
    public PaintPotView { photos = List.copyOf(photos); notes = List.copyOf(notes); allowedActions = List.copyOf(allowedActions); }
    public record Photo(String mediaId, String url, String caption, Instant addedAt) {}
    public record Note(String text, Instant addedAt) {}
    public static PaintPotView from(PaintPot pot, PaintProductView product) {
        return new PaintPotView(pot.id(), pot.paintProductId(), pot.version(), product,
                pot.condition().id(), pot.remainingLevel().id(), pot.possession().id(), pot.available(),
                pot.registeredAt(), pot.acquiredAt(), pot.openedAt(),
                pot.photos().stream().map(photo -> new Photo(photo.mediaId(), photo.url(), photo.caption(), photo.occurredAt())).toList(),
                pot.notes().stream().map(note -> new Note(note.note(), note.occurredAt())).toList(), pot.allowedActions());
    }
}
