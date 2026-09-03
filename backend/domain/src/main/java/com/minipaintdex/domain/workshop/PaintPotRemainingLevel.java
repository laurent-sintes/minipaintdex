package com.minipaintdex.domain.workshop;

public enum PaintPotRemainingLevel {
    UNKNOWN("unknown"),
    FULL("full"),
    HALF("half"),
    LOW("low"),
    EMPTY("empty");

    private final String id;
    PaintPotRemainingLevel(String id) { this.id = id; }
    public String id() { return id; }
    public static PaintPotRemainingLevel fromId(String id) {
        for (var value : values()) if (value.id.equals(id)) return value;
        throw DomainFields.invalid("Unknown PaintPotRemainingLevel: " + id);
    }
}
