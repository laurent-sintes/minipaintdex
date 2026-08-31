package com.minipaintdex.server.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/events")
final class DomainEventStreamController {
    private final DomainEventStream events;

    DomainEventStreamController(DomainEventStream events) {
        this.events = events;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter subscribe(@RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return events.subscribe(lastEventId);
    }
}
