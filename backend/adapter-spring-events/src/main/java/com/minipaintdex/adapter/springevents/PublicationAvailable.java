package com.minipaintdex.adapter.springevents;

import org.springframework.context.ApplicationEvent;

public final class PublicationAvailable extends ApplicationEvent {
    private final String publicationId;

    public PublicationAvailable(String publicationId) {
        super(publicationId);
        this.publicationId = publicationId;
    }

    public String publicationId() {
        return publicationId;
    }
}
