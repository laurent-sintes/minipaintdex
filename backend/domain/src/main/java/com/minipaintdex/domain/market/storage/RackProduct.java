package com.minipaintdex.domain.market.storage;
import com.minipaintdex.domain.shared.storage.*;

import java.util.List;

public record RackProduct(int schemaVersion, String id, String name, String brand, String reference,
        String installation, String arrangement, List<RackRowDefinition> rows, List<String> sources, String notes,
        List<Photo> photos) {
    public RackProduct {
        if (schemaVersion != 1) throw StorageFields.invalid("schemaVersion must be 1.");
        id = StorageFields.id(id); name = StorageFields.text(name, "name"); brand = StorageFields.text(brand, "brand");
        if (!List.of("desktop", "wall", "portable").contains(installation)) throw StorageFields.invalid("Invalid installation.");
        if (!List.of("tiered", "vertical", "rotating").contains(arrangement)) throw StorageFields.invalid("Invalid arrangement.");
        rows = RackRowDefinition.validateRows(rows); sources = StorageFields.sources(sources);
        if (sources.isEmpty()) throw StorageFields.invalid("Commercial racks require sources.");
        notes = notes == null ? "" : notes;
        photos = photos == null ? List.of() : List.copyOf(photos);
    }
    public record Photo(String url, String pageUrl, String credit, String usageStatus) {
        public Photo {
            StorageFields.sources(List.of(url, pageUrl));
            credit = StorageFields.text(credit, "photo.credit");
            usageStatus = StorageFields.text(usageStatus, "photo.usageStatus");
        }
    }
}
