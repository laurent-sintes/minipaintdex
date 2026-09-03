package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class PaintPotTest {
    private static final Instant AT = Instant.parse("2026-09-01T12:00:00Z");
    @Test void distinctPotsShareOnlyTheProductAndReplayTheirOwnHistory() {
        var first = PaintPot.register("pot-first", "paint-red", null, AT);
        var second = PaintPot.register("pot-second", "paint-red", null, AT);
        assertNull(first.acquiredAt());
        assertNull(first.openedAt());
        assertEquals(PaintPotRemainingLevel.UNKNOWN, first.remainingLevel());
        assertTrue(first.available());
        var history = new ArrayList<DomainEvent>(first.releaseEvents());
        first.observe(PaintPotCondition.THICKENED, PaintPotRemainingLevel.LOW, AT);
        first.open(AT);
        first.addNote("Stir before use", AT);
        first.addPhoto("media-one", "/media/workshop/pot-first/photo.png", "Mine", "photo.png", "image/png", 10, "a".repeat(64), AT);
        history.addAll(first.releaseEvents());
        var replayed = PaintPot.rehydrate(history.stream().map(PaintPotEvent.class::cast).toList());
        assertEquals(5, replayed.version());
        assertEquals(PaintPotRemainingLevel.LOW, replayed.remainingLevel());
        assertEquals(1, replayed.photos().size());
        assertEquals(PaintPotRemainingLevel.UNKNOWN, second.remainingLevel());
        assertTrue(second.photos().isEmpty());
        assertThrows(DomainException.class, () -> replayed.open(AT));
    }
    @Test void availabilityAndPossessionHaveDifferentMeanings() {
        var pot = PaintPot.register("pot-one", "paint-red", null, AT);
        pot.observe(PaintPotCondition.DRIED, PaintPotRemainingLevel.HALF, AT);
        assertEquals(PaintPotPossession.OWNED, pot.possession());
        assertFalse(pot.available());
        pot.observe(PaintPotCondition.USABLE, PaintPotRemainingLevel.EMPTY, AT);
        assertFalse(pot.available());
        pot.observe(PaintPotCondition.USABLE, PaintPotRemainingLevel.LOW, AT);
        assertTrue(pot.available());
        pot.changePossession(PaintPotPossession.GIVEN_AWAY, AT);
        assertFalse(pot.available());
        assertThrows(DomainException.class, () -> pot.observe(PaintPotCondition.USABLE, PaintPotRemainingLevel.FULL, AT));
        assertThrows(DomainException.class, () -> pot.open(AT));
        pot.changePossession(PaintPotPossession.OWNED, AT);
        assertTrue(pot.available());
    }
    @Test void stockCountsOwnedAndUsablePotsWithoutInventingUnknownObservations() {
        var first = PaintPot.register("pot-first", "paint-red", null, AT);
        var second = PaintPot.register("pot-second", "paint-red", null, AT);
        first.observe(PaintPotCondition.DRIED, PaintPotRemainingLevel.HALF, AT);
        var inventory = WorkshopPaintInventory.fromPots(java.util.List.of(first, second));
        assertEquals(new WorkshopPaintStock("paint-red", 2, 1), inventory.stocks().getFirst());
        second.changePossession(PaintPotPossession.DISCARDED, AT);
        inventory = WorkshopPaintInventory.fromPots(java.util.List.of(first, second));
        assertEquals(java.util.Set.of("paint-red"), inventory.ownedPaintProductIds());
        assertTrue(inventory.availablePaintProductIds().isEmpty());
        assertFalse(second.allowedActions().contains("observe"));
        assertFalse(second.allowedActions().contains("open"));
    }

    @Test void rejectsInvalidIdentityDatesAndMixedHistory() {
        assertThrows(DomainException.class, () -> PaintPot.register("Bad ID", "paint-red", null, AT));
        assertThrows(DomainException.class, () -> PaintPot.register("pot-one", "paint-red", AT.plusSeconds(1), AT));
        var pot = PaintPot.register("pot-one", "paint-red", AT, AT);
        assertThrows(DomainException.class, () -> pot.open(AT.minusSeconds(1)));
        assertThrows(DomainException.class, () -> PaintPot.rehydrate(java.util.List.of(
            new PaintPotEvent.PaintPotRegistered("pot-one", "paint-red", null, AT),
            new PaintPotEvent.PaintPotOpened("pot-two", AT))));
    }
}
