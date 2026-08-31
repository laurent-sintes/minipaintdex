package com.minipaintdex.domain.market.paint;

import com.minipaintdex.domain.shared.DomainException;

/** Stable functional taxonomy used for search, guidance and paint matching. */
public enum MarketPaintType {
    AIRBRUSH("airbrush"),
    AUXILIARY("auxiliary"),
    INK("ink"),
    METALLIC("metallic"),
    ONE_COAT_CONTRAST("one_coat_contrast"),
    OPAQUE_STANDARD("opaque_standard"),
    PRIMER("primer"),
    TECHNICAL_EFFECT("technical_effect"),
    WASH_SHADE("wash_shade");

    private final String id;

    MarketPaintType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean requiresUsageInstructions() {
        return this == AUXILIARY || this == INK || this == PRIMER
                || this == TECHNICAL_EFFECT || this == WASH_SHADE;
    }

    public static MarketPaintType fromId(String id) {
        for (var value : values()) if (value.id.equals(id)) return value;
        throw new DomainException("invalid_market_paint", "Unknown market paint functional type: " + id);
    }
}
