package com.minipaintdex.application.validation;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.domain.market.guide.MarketPaintingGuide;
import com.minipaintdex.domain.market.paint.MarketPaint;
import com.minipaintdex.domain.market.paint.MarketPaintLifecycle;
import com.minipaintdex.domain.market.paint.MarketPaintProfile;
import com.minipaintdex.domain.market.product.PaintableProduct;
import com.minipaintdex.domain.shared.DomainException;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Translates extensible import documents into one strictly validated typed Market generation. */
public final class MarketCatalogFactory {
    private MarketCatalogFactory() {}

    public static MarketCatalogSnapshot create(
            List<StructuredDocument> paintDocuments,
            List<PaintableProduct> products,
            List<StructuredDocument> guideDocuments) {
        var paints = paintDocuments.stream().map(MarketCatalogFactory::paint).toList();
        var guides = guideDocuments.stream().map(MarketCatalogFactory::guide).toList();
        validateGeneration(paints, products, guides);
        return new MarketCatalogSnapshot(paints, products, guides);
    }

    /** Converts and validates one complete paintable-product import document. */
    public static PaintableProduct product(StructuredDocument document) {
        var value = map(document);
        var edition = map(value.get("edition"));
        var sources = maps(value.get("sources")).stream().map(MarketCatalogFactory::productSource).toList();
        var items = maps(value.get("catalog_items")).stream().map(item -> new PaintableProduct.CatalogItem(
                text(item.get("id")), text(item.get("product_id")), text(item.get("name")),
                text(item.get("kind")), number(item.get("quantity"), null, "product.catalog_items.quantity"),
                text(item.get("description")), Boolean.TRUE.equals(item.get("assembly_required")),
                maps(item.get("reference_images")).stream().map(image -> new PaintableProduct.ReferenceImage(
                        text(image.get("url")), text(image.get("page_url")), text(image.get("credit")),
                        text(image.get("license")))).toList(),
                maps(item.get("sources")).stream().map(MarketCatalogFactory::productSource).toList())).toList();
        return new PaintableProduct(
                number(value.get("schema_version"), 1, "product.schema_version"),
                text(value.get("id")), text(value.get("name")), text(value.get("line")),
                text(value.get("product_type")), text(value.get("scope")),
                number(value.get("expected_paintable_count"), null, "product.expected_paintable_count"),
                new PaintableProduct.Edition(text(edition.get("note")), text(edition.get("url"))), sources, items);
    }

    private static PaintableProduct.Source productSource(Map<String, Object> source) {
        return new PaintableProduct.Source(
                text(source.get("kind")), text(source.get("label")), text(source.get("url")));
    }

    private static MarketPaint paint(StructuredDocument document) {
        var value = map(document);
        var color = map(value.get("color"));
        var profile = map(value.get("profile"));
        var undercoat = map(profile.get("undercoat"));
        var usage = map(value.get("usage_instructions"));
        return new MarketPaint(
                number(value.get("schema_version"), 1, "paint.schema_version"),
                text(value.get("id")), text(value.get("brand")), text(value.get("manufacturer")),
                strings(value.get("brand_aliases")), text(value.get("range")),
                new MarketPaintProfile(
                        strings(profile.get("roles")).stream().map(MarketPaintProfile.Role::fromId).toList(),
                        strings(profile.get("application_methods")).stream()
                                .map(MarketPaintProfile.ApplicationMethod::fromId).toList(),
                        MarketPaintProfile.ApplicationSystem.fromId(text(profile.get("application_system"))),
                        MarketPaintProfile.Coverage.fromId(text(profile.get("coverage"))),
                        MarketPaintProfile.Finish.fromId(text(profile.get("finish"))),
                        strings(profile.get("effects")).stream().map(MarketPaintProfile.Effect::fromId).toList(),
                        new MarketPaintProfile.Undercoat(
                                MarketPaintProfile.UndercoatTone.fromId(text(undercoat.get("tone"))),
                                Boolean.TRUE.equals(undercoat.get("pre_highlighted_surface_recommended"))),
                        MarketPaintProfile.Medium.fromId(text(profile.get("medium")))),
                text(value.get("reference")), text(value.get("name")),
                new MarketPaint.Color(text(color.get("family")), text(color.get("hex"))),
                MarketPaintLifecycle.fromId(defaultText(text(value.get("lifecycle_status")), "unknown")),
                defaultText(text(value.get("data_status")), "unreviewed"),
                strings(value.get("warnings")), strings(value.get("tags")),
                text(value.get("notes")),
                uri(value.get("manufacturer_page"), "paint.manufacturer_page"),
                image(map(value.get("manufacturer_image")), "paint.manufacturer_image"),
                number(value.get("volume_ml"), 0, "paint.volume_ml"),
                strings(value.get("recommended_uses")),
                new MarketPaint.UsageInstructions(
                        text(usage.get("summary")), strings(usage.get("steps")), strings(usage.get("tips")),
                        text(usage.get("instruction_status")), Boolean.TRUE.equals(usage.get("review_required"))),
                date(value.get("verified_at"), "paint.verified_at"),
                image(map(value.get("result_image")), "paint.result_image"));
    }

    private static MarketPaint.ImageReference image(Map<String, Object> value, String field) {
        return new MarketPaint.ImageReference(
                text(value.get("path")), uri(value.get("source_url"), field + ".source_url"),
                text(value.get("credit")), text(value.get("license")),
                uri(value.get("reference_url"), field + ".reference_url"));
    }

    private static MarketPaintingGuide guide(StructuredDocument document) {
        var value = map(document);
        var provenance = map(value.get("provenance"));
        return new MarketPaintingGuide(
                number(value.get("schema_version"), 1, "guide.schema_version"),
                text(value.get("id")), number(value.get("version"), null, "guide.version"),
                MarketPaintingGuide.KnowledgeStatus.fromId(text(value.get("knowledge_status"))),
                text(value.get("catalog_item_id")), strings(value.get("source_refs")),
                new MarketPaintingGuide.Provenance(
                        text(provenance.get("method")), Boolean.TRUE.equals(provenance.get("review_required"))),
                maps(value.get("sources")).stream().map(source -> new MarketPaintingGuide.Source(
                        text(source.get("kind")), text(source.get("label")),
                        uri(source.get("url"), "guide.sources.url"))).toList(),
                maps(value.get("slots")).stream().map(MarketCatalogFactory::slot).toList(),
                maps(value.get("preparation")).stream().map(MarketCatalogFactory::step).toList(),
                maps(value.get("painting")).stream().map(MarketCatalogFactory::step).toList());
    }

    private static MarketPaintingGuide.Slot slot(Map<String, Object> value) {
        var requested = map(value.get("requested_paint"));
        return new MarketPaintingGuide.Slot(
                text(value.get("id")), text(value.get("role")), text(value.get("market_paint_id")),
                Boolean.TRUE.equals(value.get("pending_import")),
                new MarketPaintingGuide.RequestedPaint(
                        text(requested.get("brand")), text(requested.get("name")), text(requested.get("color_hex"))));
    }

    private static MarketPaintingGuide.Step step(Map<String, Object> value) {
        return new MarketPaintingGuide.Step(text(value.get("title")), text(value.get("detail")));
    }

    private static void validateGeneration(
            List<MarketPaint> paints,
            List<PaintableProduct> products,
            List<MarketPaintingGuide> guides) {
        var paintIds = uniqueIds(paints.stream().map(MarketPaint::id).toList(), "market paint");
        uniqueIds(products.stream().map(PaintableProduct::id).toList(), "paintable product");
        var catalogItemIds = uniqueIds(products.stream()
                .flatMap(product -> product.catalogItems().stream()).map(PaintableProduct.CatalogItem::id).toList(),
                "catalog item");
        var guideVersions = new HashSet<String>();
        for (var guide : guides) {
            if (!guideVersions.add(guide.id() + "@" + guide.version())) {
                throw invalid("Duplicate market painting guide version: " + guide.id() + "@" + guide.version());
            }
            if (!catalogItemIds.contains(guide.catalogItemId())) {
                throw invalid("Guide " + guide.id() + " references unknown catalog item " + guide.catalogItemId());
            }
            for (var slot : guide.slots()) {
                if (slot.marketPaintId() != null && !paintIds.contains(slot.marketPaintId())) {
                    throw invalid("Guide slot " + slot.id() + " references unknown market paint " + slot.marketPaintId());
                }
            }
        }
    }

    private static HashSet<String> uniqueIds(List<String> ids, String kind) {
        var result = new HashSet<String>();
        for (var id : ids) if (!result.add(id)) throw invalid("Duplicate " + kind + " id: " + id);
        return result;
    }

    private static Map<String, Object> map(StructuredDocument document) {
        var result = new LinkedHashMap<String, Object>();
        document.fields().forEach(field -> {
            if (result.putIfAbsent(field.name(), value(field.value())) != null) {
                throw invalid("Duplicate document field: " + field.name());
            }
        });
        return Collections.unmodifiableMap(result);
    }

    private static Object value(StructuredDocument.Value value) {
        if (value == null) throw invalid("Document values cannot be null.");
        return switch (value) {
            case StructuredDocument.Text text -> text.value();
            case StructuredDocument.NumberValue number -> number.value();
            case StructuredDocument.BooleanValue bool -> bool.value();
            case StructuredDocument.NullValue ignored -> null;
            case StructuredDocument.ArrayValue array -> array.values().stream().map(MarketCatalogFactory::value).toList();
            case StructuredDocument.ObjectValue object -> map(object.value());
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        if (list.stream().anyMatch(entry -> !(entry instanceof Map<?, ?>))) {
            throw invalid("Expected a list of objects.");
        }
        return list.stream().map(MarketCatalogFactory::map).toList();
    }

    private static List<String> strings(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return list.stream().map(MarketCatalogFactory::text)
                .filter(entry -> !entry.isBlank()).toList();
        var single = text(value);
        return single.isBlank() ? List.of() : List.of(single);
    }

    private static int number(Object value, Integer fallback, String field) {
        if (value == null || text(value).isBlank()) {
            if (fallback != null) return fallback;
            throw invalid(field + " is required.");
        }
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException exception) {
            throw invalid(field + " must be an integer.");
        }
    }

    private static LocalDate date(Object value, String field) {
        var text = text(value);
        if (text.isBlank()) return null;
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            throw invalid(field + " must use ISO-8601 date format.");
        }
    }

    private static URI uri(Object value, String field) {
        var text = text(value);
        if (text.isBlank()) return null;
        try {
            var uri = URI.create(text);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw invalid(field + " must use HTTP or HTTPS.");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw invalid(field + " is not a valid URI.");
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String defaultText(String value, String fallback) { return value.isBlank() ? fallback : value; }
    private static DomainException invalid(String message) {
        return new DomainException("invalid_market_catalog", message);
    }
}
