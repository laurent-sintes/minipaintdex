package com.minipaintdex.application.port;

/** Binary storage boundary for workshop progress media. */
public interface WorkshopMediaStorage {
    /** Durably stores validated bytes under a generated media identity and returns public metadata. */
    StoredMedia store(String ownerAggregateId, String mediaId, String originalFilename, String contentType, byte[] content);

    /** Removes precisely the supplied stored object; implementations must reject path traversal. */
    void delete(StoredMedia media);

    record StoredMedia(
            String id,
            String publicPath,
            String storagePath,
            String originalFilename,
            String contentType,
            long size,
            String sha256) {
    }
}
