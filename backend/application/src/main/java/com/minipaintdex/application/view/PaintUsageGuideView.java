package com.minipaintdex.application.view;

import com.minipaintdex.domain.market.paint.PaintUsageGuide;
import java.net.URI;
import java.util.List;

public record PaintUsageGuideView(String paintUsageGuideId, String title, String brand, List<String> ranges,
        int revision, String originalLanguage, String language, PaintUsageGuide.Content content,
        String knowledgeStatus, boolean reviewRequired, String translationStatus,
        boolean translationReviewRequired, List<URI> sourceUrls) {
    public PaintUsageGuideView { ranges = List.copyOf(ranges); sourceUrls = List.copyOf(sourceUrls); }
    public static PaintUsageGuideView from(PaintUsageGuide guide, String language) {
        var selected = guide.select(language);
        return new PaintUsageGuideView(guide.id(), guide.title(), guide.brand(), guide.ranges(), guide.revision(),
                guide.originalLanguage(), selected.language(), selected.content(), guide.knowledgeStatus(),
                guide.reviewRequired(), selected.translationStatus(), selected.translationReviewRequired(), guide.sourceUrls());
    }
}
