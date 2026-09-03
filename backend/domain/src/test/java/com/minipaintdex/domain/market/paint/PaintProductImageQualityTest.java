package com.minipaintdex.domain.market.paint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaintProductImageQualityTest {
    @Test void ordersCandidatesByProvenanceIncludingEqualQualityReplacement() {
        for (var existing : PaintProductImageQuality.values()) {
            for (var candidate : PaintProductImageQuality.values()) {
                assertEquals(candidate.rank() <= existing.rank(), candidate.isAtLeastAsGoodAs(existing));
            }
        }
        assertFalse(PaintProductImageQuality.OWNED_PHOTO.isAtLeastAsGoodAs(PaintProductImageQuality.OFFICIAL_PHOTO));
        assertFalse(PaintProductImageQuality.OWNED_PHOTO.isAtLeastAsGoodAs(PaintProductImageQuality.RETAILER_PHOTO));
        assertTrue(PaintProductImageQuality.OWNED_PHOTO.isAtLeastAsGoodAs(PaintProductImageQuality.OWNED_PHOTO));
    }
}
