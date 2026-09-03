package com.minipaintdex.domain.workshop;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Representative personal photo from currently owned pots of one paint product. */
public record PaintPotPhotoSelection(String paintPotId, PaintPotEvent.PaintPotPhotoAdded photo) {
    /** Latest dated photo of an owned pot; stable media identity breaks timestamp ties. */
    public static Optional<PaintPotPhotoSelection> select(List<PaintPot> pots) {
        return pots.stream().filter(pot -> pot.possession() == PaintPotPossession.OWNED)
                .flatMap(pot -> pot.photos().stream().map(photo -> new PaintPotPhotoSelection(pot.id(), photo)))
                .max(Comparator.comparing((PaintPotPhotoSelection selection) -> selection.photo().occurredAt())
                        .thenComparing(selection -> selection.photo().mediaId()).thenComparing(PaintPotPhotoSelection::paintPotId));
    }
}
