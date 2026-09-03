package com.minipaintdex.domain.workshop;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PaintPotPhotoSelectionTest {
    private static final Instant AT = Instant.parse("2026-09-01T12:00:00Z");

    @Test void selectsNewestOwnedPhotoAndBreaksTiesDeterministically() {
        var first = pot("pot-a", "photo-a", AT);
        var second = pot("pot-b", "photo-b", AT);
        var removed = pot("pot-c", "photo-c", AT.plusSeconds(100));
        removed.changePossession(PaintPotPossession.DISCARDED, AT.plusSeconds(101));
        assertEquals("pot-b", PaintPotPhotoSelection.select(List.of(first, removed, second)).orElseThrow().paintPotId());
        assertEquals("pot-b", PaintPotPhotoSelection.select(List.of(second, first, removed)).orElseThrow().paintPotId());
        first.addPhoto("photo-new", "/new.png", "", "new.png", "image/png", 1, "a".repeat(64), AT.plusSeconds(1));
        assertEquals("photo-new", PaintPotPhotoSelection.select(List.of(first, second)).orElseThrow().photo().mediaId());
        assertEquals(2, first.photos().size());
        assertTrue(PaintPotPhotoSelection.select(List.of(removed)).isEmpty());
        assertTrue(PaintPotPhotoSelection.select(List.of(PaintPot.register("empty", "paint", null, AT))).isEmpty());
    }

    private static PaintPot pot(String id, String media, Instant at) {
        var pot = PaintPot.register(id, "paint", null, AT);
        pot.addPhoto(media, "/" + media + ".png", "", media + ".png", "image/png", 1, "a".repeat(64), at);
        return pot;
    }
}
