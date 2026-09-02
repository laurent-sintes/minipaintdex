package com.minipaintdex.domain.market.paint;

import com.minipaintdex.domain.shared.DomainException;

import java.util.Arrays;

/** Controlled reason why a manufacturer image has not reached official-photo quality. */
public enum MarketPaintImageLimitationCode {
    OFFICIAL_PHOTO_NOT_PUBLISHED("official-photo-not-published"),
    OFFICIAL_SOURCE_UNAVAILABLE("official-source-unavailable"),
    OFFICIAL_CANDIDATE_REJECTED("official-candidate-rejected"),
    OFFICIAL_REFERENCE_UNMATCHED("official-reference-unmatched"),
    BETTER_SOURCE_NOT_FOUND("better-source-not-found"),
    MANUALLY_PROVIDED("manually-provided"),
    HISTORICAL_REASON_NOT_RECORDED("historical-reason-not-recorded");

    private final String id;

    MarketPaintImageLimitationCode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static MarketPaintImageLimitationCode fromId(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(values()).filter(item -> item.id.equals(value)).findFirst()
                .orElseThrow(() -> new DomainException(
                        "invalid_market_paint", "Unknown market paint image limitation: " + value));
    }
}
