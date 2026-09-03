package com.minipaintdex.domain.workshop;

public enum PaintPotPossession {
    OWNED("owned"),
    GIVEN_AWAY("given-away"),
    DISCARDED("discarded");

    private final String id;
    PaintPotPossession(String id) { this.id = id; }
    public String id() { return id; }
    public static PaintPotPossession fromId(String id) {
        for (var value : values()) if (value.id.equals(id)) return value;
        throw DomainFields.invalid("Unknown PaintPotPossession: " + id);
    }
}
