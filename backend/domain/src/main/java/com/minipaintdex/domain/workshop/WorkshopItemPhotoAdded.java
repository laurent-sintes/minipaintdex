package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopItemPhotoAdded(
        String workshopItemId, String paintingProjectId, String mediaId, String url,
        String stage, String caption, String originalFilename, String contentType,
        long size, String sha256, Instant occurredAt) implements WorkshopItemEvent {
    public WorkshopItemPhotoAdded {
        workshopItemId = DomainFields.required(workshopItemId, "workshopItemId");
        paintingProjectId = DomainFields.required(paintingProjectId, "paintingProjectId");
        mediaId = DomainFields.required(mediaId, "mediaId");
        url = DomainFields.required(url, "url");
        stage = stage == null ? "" : stage;
        caption = caption == null ? "" : caption;
        originalFilename = DomainFields.required(originalFilename, "originalFilename");
        contentType = DomainFields.required(contentType, "contentType");
        if (size < 0) throw DomainFields.invalid("size cannot be negative.");
        sha256 = DomainFields.required(sha256, "sha256");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "workshop_item.photo_added"; }
    @Override public String aggregateId() { return workshopItemId; }
    @Override public String projectId() { return paintingProjectId; }
}
