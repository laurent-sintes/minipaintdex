package com.minipaintdex.application.view;

import java.util.List;

/** Read-only completeness indicators for administering the canonical market-paint catalog. */
public record PaintCatalogQualityView(
        int total,
        int missingColorHex,
        int missingColorFamily,
        int unknownFinish,
        int unknownCoverage,
        int technicalReviewRequired,
        int sourcedImagesWithoutLicense,
        int realResultImages,
        List<ImageQualityCount> imageQualities,
        List<ImageLimitationCount> imageLimitations) {
    public PaintCatalogQualityView {
        imageQualities = List.copyOf(imageQualities);
        imageLimitations = List.copyOf(imageLimitations);
    }

    public record ImageQualityCount(String quality, int count) {}

    public record ImageLimitationCount(String brand, String code, int count) {}
}
