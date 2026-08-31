package com.minipaintdex.application.event;

public record EventBusState(
        boolean running,
        boolean accepting,
        int recoverablePublications,
        int deadLetterPublications) {
}
