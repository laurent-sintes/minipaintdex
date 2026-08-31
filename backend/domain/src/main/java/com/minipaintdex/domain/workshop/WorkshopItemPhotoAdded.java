package com.minipaintdex.domain.workshop;

import java.time.Instant;

public record WorkshopItemPhotoAdded(
        String workshopItemId, String paintingProjectId, String mediaId, String url,
        WorkflowStage stage, String caption, String originalFilename, String contentType,
        long size, String sha256, Instant occurredAt) implements WorkshopItemEvent {
    public WorkshopItemPhotoAdded {
        workshopItemId = DomainFields.id(workshopItemId, "workshopItemId");
        paintingProjectId = DomainFields.id(paintingProjectId, "paintingProjectId");
        mediaId = DomainFields.id(mediaId, "mediaId");
        url = DomainFields.required(url, "url");
        caption = caption == null ? "" : caption;
        originalFilename = DomainFields.required(originalFilename, "originalFilename");
        contentType = DomainFields.required(contentType, "contentType");
        if (!contentType.startsWith("image/")) throw DomainFields.invalid("contentType must describe an image.");
        if (size < 1) throw DomainFields.invalid("size must be positive.");
        sha256 = DomainFields.required(sha256, "sha256");
        if (!sha256.matches("[a-f0-9]{64}")) throw DomainFields.invalid("sha256 must contain 64 lowercase hex digits.");
        occurredAt = DomainFields.required(occurredAt, "occurredAt");
    }
    @Override public String eventType() { return "workshop_item.photo_added"; }
    @Override public String aggregateId() { return workshopItemId; }
    @Override public String projectId() { return paintingProjectId; }
}
