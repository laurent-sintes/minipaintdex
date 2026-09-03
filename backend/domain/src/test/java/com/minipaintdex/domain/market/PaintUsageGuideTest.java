package com.minipaintdex.domain.market;

import com.minipaintdex.domain.market.paint.PaintUsageGuide;
import com.minipaintdex.domain.market.paint.PaintUsageGuide.Content;
import com.minipaintdex.domain.market.paint.PaintUsageGuide.Translation;
import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PaintUsageGuideTest {
    private static final Content EN = new Content("Apply carefully", List.of("Shake"), List.of("Read the label"));
    private static final Content FR = new Content("Appliquer avec soin", List.of("Agiter"), List.of("Lire l’étiquette"));
    static PaintUsageGuide guide(int revision, Content content, List<Translation> translations) {
        return new PaintUsageGuide(1, "guide", "Brand", "Range", revision, List.of("Range"), "en", content,
                "generic-template", true, List.of(URI.create("https://example.com/guide")), translations);
    }
    @Test void selectsOnlyCurrentTranslationsAndKeepsSourceAuthority() {
        var source = guide(1, EN, List.of(new Translation("fr", 1, "machine", true, FR)));
        assertEquals(FR, source.select("fr").content());
        assertTrue(source.select("fr").translationReviewRequired());
        assertEquals(EN, source.select("original").content());
        var changed = guide(2, new Content("Updated", EN.steps(), EN.tips()), source.translations());
        source.validateReplacement(changed);
        assertEquals("stale-translation", changed.select("fr").translationStatus());
        assertEquals("en", changed.select("fr").language());
        assertEquals("generic-template", changed.knowledgeStatus());
        assertEquals("missing-translation", guide(1, EN, List.of()).select("fr").translationStatus());
    }
    @Test void rejectsLostPrecautionsFutureTranslationsAndUnversionedSourceChanges() {
        assertThrows(DomainException.class, () -> guide(1, EN, List.of(new Translation("fr", 1, "machine", true, new Content("Texte", List.of("Agiter"), List.of())))));
        assertThrows(DomainException.class, () -> guide(1, EN, List.of(new Translation("fr", 2, "machine", true, FR))));
        assertThrows(DomainException.class, () -> new Translation("fr", 1, "machine", false, FR));
        assertThrows(DomainException.class, () -> guide(1, EN, List.of()).validateReplacement(guide(1, FR, List.of())));
        assertThrows(DomainException.class, () -> guide(1, EN, List.of()).validateReplacement(guide(3, FR, List.of())));
        assertThrows(DomainException.class, () -> guide(1, EN, List.of()).select("de"));
    }
    @Test void permitsReviewedTranslationCorrectionsWithoutChangingSourceRevision() {
        var source = guide(1, EN, List.of());
        source.validateReplacement(guide(1, EN, List.of(new Translation("fr", 1, "human", false, FR))));
    }
}
