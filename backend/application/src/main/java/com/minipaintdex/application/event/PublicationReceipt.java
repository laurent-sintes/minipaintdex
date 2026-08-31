package com.minipaintdex.application.event;

import java.time.Instant;

/** Result returned once a command's event batch has been accepted durably. */
public record PublicationReceipt(
        String publicationId,
        EventPublicationStatus status,
        Instant acceptedAt,
        String correlationId) {
}
