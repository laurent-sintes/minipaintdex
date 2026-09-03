package com.minipaintdex.domain.market.paint;

import com.minipaintdex.domain.shared.DomainException;
import java.net.URI;
import java.util.List;

/** A sourced commercial publication, never a scraping execution or a paint identity. */
public record PaintCatalogEdition(
        int schemaVersion, String id, String brand, String title, String editionLabel,
        Integer publicationYear, List<String> ranges, List<URI> sourceUrls) {
    public PaintCatalogEdition {
        if (schemaVersion != 1) throw invalid("schemaVersion must be 1");
        id = identifier(id);
        brand = required(brand, "brand");
        title = required(title, "title");
        editionLabel = required(editionLabel, "editionLabel");
        if (publicationYear != null && (publicationYear < 1 || publicationYear > 9999)) {
            throw invalid("publicationYear must be a positive four-digit-or-shorter year");
        }
        if (ranges == null || ranges.isEmpty() || ranges.stream().anyMatch(value -> value == null || value.isBlank())
                || ranges.stream().distinct().count() != ranges.size()) throw invalid("ranges must be explicit and unique");
        ranges = List.copyOf(ranges);
        if (sourceUrls == null || sourceUrls.isEmpty() || sourceUrls.stream().distinct().count() != sourceUrls.size()) {
            throw invalid("sourceUrls must be nonempty and unique");
        }
        sourceUrls.forEach(PaintCatalogEdition::validateSource);
        sourceUrls = List.copyOf(sourceUrls);
    }

    public record Membership(String catalogEditionId, URI sourceUrl, String locator) {
        public Membership {
            catalogEditionId = identifier(catalogEditionId);
            validateSource(sourceUrl);
            locator = required(locator, "membership.locator");
        }
    }

    private static String identifier(String value) {
        if (value == null || !value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw invalid("Invalid stable identifier");
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " is required");
        return value;
    }

    private static void validateSource(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw invalid("Sources must be absolute HTTPS URLs");
        }
    }

    private static DomainException invalid(String message) {
        return new DomainException("invalid_paint_catalog_edition", message);
    }
}
