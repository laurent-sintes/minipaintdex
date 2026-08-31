package com.minipaintdex.domain.market.paint;

import com.minipaintdex.domain.shared.DomainException;

/** Commercial lifecycle of a market paint reference. */
public enum MarketPaintLifecycle {
    ACTIVE("active"),
    DISCONTINUED("discontinued"),
    RETIRED("retired"),
    UNKNOWN("unknown");

    private final String id;

    MarketPaintLifecycle(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static MarketPaintLifecycle fromId(String id) {
        for (var value : values()) if (value.id.equals(id)) return value;
        throw new DomainException("invalid_market_paint", "Unknown market paint lifecycle: " + id);
    }
}
