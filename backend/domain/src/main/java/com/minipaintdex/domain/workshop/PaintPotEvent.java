package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import java.time.Instant;

public sealed interface PaintPotEvent extends DomainEvent {
    String paintPotId();
    @Override default String aggregateId() { return paintPotId(); }
    @Override default String aggregateType() { return "paint_pot"; }
    @Override default String scopePaintingProjectId() { return null; }

    record PaintPotRegistered(String paintPotId, String paintProductId, Instant acquiredAt, Instant occurredAt) implements PaintPotEvent {
        public PaintPotRegistered {
            paintPotId = DomainFields.id(paintPotId, "paintPotId");
            paintProductId = DomainFields.id(paintProductId, "paintProductId");
            occurredAt = DomainFields.required(occurredAt, "occurredAt");
            if (acquiredAt != null && acquiredAt.isAfter(occurredAt)) throw DomainFields.invalid("Acquisition cannot follow registration.");
        }
        @Override public String eventType() { return "paint_pot.registered"; }
    }
    record PaintPotObserved(String paintPotId, PaintPotCondition condition, PaintPotRemainingLevel remainingLevel, Instant occurredAt) implements PaintPotEvent {
        public PaintPotObserved {
            paintPotId = DomainFields.id(paintPotId, "paintPotId");
            if (condition == null || remainingLevel == null) throw DomainFields.invalid("Condition and remaining level are required.");
            occurredAt = DomainFields.required(occurredAt, "occurredAt");
        }
        @Override public String eventType() { return "paint_pot.observed"; }
    }
    record PaintPotOpened(String paintPotId, Instant occurredAt) implements PaintPotEvent {
        public PaintPotOpened {
            paintPotId = DomainFields.id(paintPotId, "paintPotId");
            occurredAt = DomainFields.required(occurredAt, "occurredAt");
        }
        @Override public String eventType() { return "paint_pot.opened"; }
    }
    record PaintPotPossessionChanged(String paintPotId, PaintPotPossession possession, Instant occurredAt) implements PaintPotEvent {
        public PaintPotPossessionChanged {
            paintPotId = DomainFields.id(paintPotId, "paintPotId");
            if (possession == null) throw DomainFields.invalid("Possession is required.");
            occurredAt = DomainFields.required(occurredAt, "occurredAt");
        }
        @Override public String eventType() { return "paint_pot.possession_changed"; }
    }
    record PaintPotNoteAdded(String paintPotId, String note, Instant occurredAt) implements PaintPotEvent {
        public PaintPotNoteAdded {
            paintPotId = DomainFields.id(paintPotId, "paintPotId");
            note = DomainFields.required(note, "note");
            occurredAt = DomainFields.required(occurredAt, "occurredAt");
        }
        @Override public String eventType() { return "paint_pot.note_added"; }
    }
    record PaintPotPhotoAdded(String paintPotId, String mediaId, String url, String caption,
            String originalFilename, String contentType, long size, String sha256, Instant occurredAt) implements PaintPotEvent {
        public PaintPotPhotoAdded {
            paintPotId = DomainFields.id(paintPotId, "paintPotId");
            mediaId = DomainFields.id(mediaId, "mediaId");
            url = DomainFields.required(url, "url");
            caption = caption == null ? "" : caption;
            originalFilename = DomainFields.required(originalFilename, "originalFilename");
            contentType = DomainFields.required(contentType, "contentType");
            if (!contentType.startsWith("image/") || size < 1) throw DomainFields.invalid("Invalid pot photo.");
            if (sha256 == null || !sha256.matches("[a-f0-9]{64}")) throw DomainFields.invalid("Invalid photo SHA-256.");
            occurredAt = DomainFields.required(occurredAt, "occurredAt");
        }
        @Override public String eventType() { return "paint_pot.photo_added"; }
    }
}
