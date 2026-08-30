package com.minipaintdex.domain.event;

public record Actor(String type, String id) {
    public Actor {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("actor type is required");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("actor id is required");
    }
}
