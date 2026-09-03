package com.minipaintdex.domain.workshop;

/** Optional derivative of a personal photo; the photo event retains the original separately. */
public record PaintPotPhotoCutout(String mediaId, String url, long size, String sha256, String processingMethod) {
    public PaintPotPhotoCutout {
        mediaId = DomainFields.id(mediaId, "mediaId");
        url = DomainFields.required(url, "url");
        processingMethod = DomainFields.id(processingMethod, "processingMethod");
        if (size < 1 || sha256 == null || !sha256.matches("[a-f0-9]{64}"))
            throw DomainFields.invalid("Invalid pot photo cutout.");
    }
}
