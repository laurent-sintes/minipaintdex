package com.minipaintdex.application.view;

/**
 * Workshop-owned stock composed from a market reference and the personal quantity.
 * The market reference remains immutable; ownership belongs exclusively to the workshop context.
 */
public record WorkshopPaintStockView(PaintProductView paintProduct, int quantity, int availableQuantity,
        PersonalPhoto personalPhoto, boolean canReplacePhoto) {
    public record PersonalPhoto(String paintPotId, String mediaId, String url, String originalUrl,
            String processingMethod, String caption, java.time.Instant addedAt) {
        public static PersonalPhoto from(com.minipaintdex.domain.workshop.PaintPotPhotoSelection selection) {
            var photo = selection.photo();
            return new PersonalPhoto(selection.paintPotId(), photo.mediaId(),
                    photo.cutout() == null ? photo.url() : photo.cutout().url(), photo.url(),
                    photo.cutout() == null ? null : photo.cutout().processingMethod(), photo.caption(), photo.occurredAt());
        }
    }
    public WorkshopPaintStockView {
        if (paintProduct == null) throw new IllegalArgumentException("paintProduct is required.");
        if (quantity < 0) throw new IllegalArgumentException("quantity cannot be negative.");
        if (availableQuantity < 0 || availableQuantity > quantity) throw new IllegalArgumentException("Invalid available quantity.");
    }
}
