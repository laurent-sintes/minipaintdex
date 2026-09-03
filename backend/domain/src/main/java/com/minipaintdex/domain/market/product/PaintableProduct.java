package com.minipaintdex.domain.market.product;

import com.minipaintdex.domain.shared.DomainException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root for a paintable product in the market catalog.
 *
 * <p>A product describes public reference knowledge only. Ownership and progress belong to the
 * workshop bounded context.</p>
 */
public record PaintableProduct(
        int schemaVersion,
        String id,
        String name,
        String line,
        String productType,
        String scope,
        int expectedPaintableCount,
        Edition edition,
        List<Source> sources,
        List<PaintableComponent> paintableComponents) {

    public PaintableProduct {
        if (schemaVersion != 1) throw invalid("schemaVersion must be 1.");
        requireId(id, "product id");
        require(name, "product name");
        require(line, "product line");
        require(productType, "product type");
        require(scope, "product scope");
        if (expectedPaintableCount < 1) throw invalid("expectedPaintableCount must be positive.");
        edition = Objects.requireNonNullElse(edition, new Edition("", ""));
        sources = sources == null ? List.of() : List.copyOf(sources);
        paintableComponents = paintableComponents == null ? List.of() : List.copyOf(paintableComponents);
        if (paintableComponents.isEmpty()) throw invalid("A product must contain at least one paintable component.");

        var ids = new HashSet<String>();
        var total = 0;
        for (var item : paintableComponents) {
            if (!id.equals(item.paintableProductId())) {
                throw invalid("Paintable component " + item.id() + " must reference product " + id + ".");
            }
            if (!ids.add(item.id())) throw invalid("Duplicate paintable component id: " + item.id());
            total += item.quantity();
        }
        if (total != expectedPaintableCount) {
            throw invalid("Catalog quantities total " + total + " but expectedPaintableCount is " + expectedPaintableCount + ".");
        }
    }

    public PaintableComponent paintableComponent(String paintableComponentId) {
        return paintableComponents.stream()
                .filter(item -> item.id().equals(paintableComponentId))
                .findFirst()
                .orElseThrow(() -> new DomainException("not_found", "Paintable component not found: " + paintableComponentId));
    }

    public record Edition(String note, String url) {
        public Edition {
            note = note == null ? "" : note;
            url = url == null ? "" : url;
        }
    }

    public record Source(String kind, String label, String url) {
        public Source {
            require(kind, "source kind");
            require(label, "source label");
            require(url, "source url");
        }
    }

    public record ReferenceImage(String url, String pageUrl, String credit, String license) {
        public ReferenceImage {
            require(url, "image url");
            pageUrl = pageUrl == null ? "" : pageUrl;
            credit = credit == null ? "" : credit;
            license = license == null ? "" : license;
        }
    }

    public record PaintableComponent(
            String id,
            String paintableProductId,
            String name,
            String kind,
            int quantity,
            String description,
            boolean assemblyRequired,
            List<ReferenceImage> referenceImages,
            List<Source> sources) {
        public PaintableComponent {
            requireId(id, "paintable component id");
            requireId(paintableProductId, "paintable component product id");
            require(name, "paintable component name");
            require(kind, "paintable component kind");
            if (quantity < 1) throw invalid("Paintable component quantity must be positive for " + id + ".");
            description = description == null ? "" : description;
            referenceImages = referenceImages == null ? List.of() : List.copyOf(referenceImages);
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    private static void requireId(String value, String field) {
        require(value, field);
        if (!value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw invalid(field + " must use lowercase kebab-case.");
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " is required.");
    }

    private static DomainException invalid(String message) {
        return new DomainException("invalid_product", message);
    }
}
