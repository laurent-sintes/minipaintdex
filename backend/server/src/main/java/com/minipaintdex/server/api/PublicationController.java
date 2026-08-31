package com.minipaintdex.server.api;

import com.minipaintdex.application.port.EventBus;
import com.minipaintdex.domain.shared.DomainException;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/publications")
final class PublicationController {
    private final EventBus eventBus;

    PublicationController(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @GetMapping("/{publicationId}")
    EntityModel<?> publication(@PathVariable String publicationId) {
        var publication = eventBus.publication(publicationId).orElseThrow(() ->
                new DomainException("not_found", "Event publication not found: " + publicationId));
        return EntityModel.of(publication,
                Link.of("/api/v1/publications/" + publicationId).withSelfRel(),
                Link.of("/api/v1/activity").withRel("activity"));
    }
}
