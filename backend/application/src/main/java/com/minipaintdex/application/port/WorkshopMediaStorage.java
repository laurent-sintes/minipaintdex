package com.minipaintdex.application.port;

public interface WorkshopMediaStorage {
    StoredMedia store(String itemId, String mediaId, String originalFilename, String contentType, byte[] content);

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
