package com.minipaintdex.domain.market.paint;

import com.minipaintdex.domain.shared.DomainException;

import java.util.Arrays;

/** Canonical provenance quality of a market-paint image; a lower rank is better. */
public enum PaintProductImageQuality {
    OFFICIAL_PHOTO("official_photo", 1),
    RETAILER_PHOTO("retailer_photo", 2),
    OWNED_PHOTO("owned_photo", 3),
    GENERIC_VISUAL("generic_visual", 4),
    COLOR_SWATCH("color_swatch", 5),
    NONE("none", 6);

    private final String id;
    private final int rank;

    PaintProductImageQuality(String id, int rank) {
        this.id = id;
        this.rank = rank;
    }

    public String id() { return id; }
    public int rank() { return rank; }
    public boolean isAtLeastAsGoodAs(PaintProductImageQuality other) { return rank <= other.rank; }

    public static PaintProductImageQuality fromId(String value) {
        if (value == null || value.isBlank()) return NONE;
        return Arrays.stream(values()).filter(item -> item.id.equals(value)).findFirst()
                .orElseThrow(() -> new DomainException(
                        "invalid_market_paint", "Unknown market paint image quality: " + value));
    }
}
