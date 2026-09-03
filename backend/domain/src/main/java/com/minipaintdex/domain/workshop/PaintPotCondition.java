package com.minipaintdex.domain.workshop;

public enum PaintPotCondition {
    UNKNOWN("unknown"),
    USABLE("usable"),
    THICKENED("thickened"),
    DRIED("dried");

    private final String id;
    PaintPotCondition(String id) { this.id = id; }
    public String id() { return id; }
    public static PaintPotCondition fromId(String id) {
        for (var value : values()) if (value.id.equals(id)) return value;
        throw DomainFields.invalid("Unknown PaintPotCondition: " + id);
    }
}
