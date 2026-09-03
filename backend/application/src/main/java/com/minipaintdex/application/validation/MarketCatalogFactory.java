package com.minipaintdex.application.validation;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.domain.market.guide.MarketPaintingGuide;
import com.minipaintdex.domain.market.paint.PaintProduct;
import com.minipaintdex.domain.market.paint.PaintCatalogEdition;
import com.minipaintdex.domain.market.paint.PaintProductLifecycle;
import com.minipaintdex.domain.market.paint.PaintProductImageQuality;
import com.minipaintdex.domain.market.paint.PaintProductImageLimitationCode;
import com.minipaintdex.domain.market.paint.PaintProductProfile;
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
            List<StructuredDocument> guideDocuments,
            List<StructuredDocument> editionDocuments, List<StructuredDocument> usageGuideDocuments) {
        var paints = paintDocuments.stream().map(MarketCatalogFactory::paint).toList();
        var guides = guideDocuments.stream().map(MarketCatalogFactory::guide).toList();
        validateGeneration(paints, products, guides);
        var editions = editionDocuments.stream().map(MarketCatalogFactory::catalogEdition).toList();
        uniqueIds(editions.stream().map(PaintCatalogEdition::id).toList(), "paint catalog edition");
        var byId = editions.stream().collect(java.util.stream.Collectors.toMap(PaintCatalogEdition::id, e -> e));
        for (var paint : paints) for (var membership : paint.catalogMemberships()) {
            var edition = byId.get(membership.catalogEditionId());
            if (edition == null || !edition.brand().equals(paint.brand())) {
                throw invalid("Unknown or cross-brand catalog edition for paint " + paint.id());
            }
            if (!edition.ranges().contains(paint.range()) || !edition.sourceUrls().contains(membership.sourceUrl())) {
                throw invalid("Catalog membership is outside documented source/range scope: " + paint.id());
            }
        }
        var usageGuides = usageGuideDocuments.stream().map(MarketCatalogFactory::paintUsageGuide).toList();
        uniqueIds(usageGuides.stream().map(com.minipaintdex.domain.market.paint.PaintUsageGuide::id).toList(), "paint usage guide");
        var usageById = usageGuides.stream().collect(java.util.stream.Collectors.toMap(com.minipaintdex.domain.market.paint.PaintUsageGuide::id, g -> g));
        for (var paint : paints) {
            for (var id : paint.usageGuideIds()) {
                var usageGuide = usageById.get(id);
                if (usageGuide == null || !usageGuide.appliesTo(paint)) throw invalid("Unknown or out-of-scope usage guide for " + paint.id());
            }
            if (paint.profile().requiresUsageInstructions() && !paint.usageInstructions().complete()
                    && paint.usageGuideIds().stream().map(usageById::get).noneMatch(g -> !g.original().steps().isEmpty())) {
                throw invalid("Technical paint requires actionable usage instructions: " + paint.id());
            }
        }
        return new MarketCatalogSnapshot(paints, products, guides, editions, usageGuides);
    }

    public static com.minipaintdex.domain.market.paint.PaintUsageGuide paintUsageGuide(StructuredDocument document) {
        var value = map(document);
        return new com.minipaintdex.domain.market.paint.PaintUsageGuide(
                number(value.get("schema_version"), null, "guide.schema_version"), text(value.get("id")),
                text(value.get("brand")), text(value.get("title")), number(value.get("revision"), null, "guide.revision"),
                strings(value.get("ranges")), text(value.get("original_language")), usageContent(map(value.get("original"))),
                text(value.get("knowledge_status")), Boolean.TRUE.equals(value.get("review_required")),
                strings(value.get("source_urls")).stream().map(url -> uri(url, "guide.source_urls")).toList(),
                maps(value.get("translations")).stream().map(t -> new com.minipaintdex.domain.market.paint.PaintUsageGuide.Translation(
                        text(t.get("language")), number(t.get("source_revision"), null, "translation.source_revision"),
                        text(t.get("method")), Boolean.TRUE.equals(t.get("review_required")), usageContent(map(t.get("content"))))).toList());
    }

    private static com.minipaintdex.domain.market.paint.PaintUsageGuide.Content usageContent(Map<String, Object> value) {
        return new com.minipaintdex.domain.market.paint.PaintUsageGuide.Content(text(value.get("summary")), strings(value.get("steps")), strings(value.get("tips")));
    }

    public static PaintCatalogEdition catalogEdition(StructuredDocument document) {
        var value = map(document);
        return new PaintCatalogEdition(number(value.get("schema_version"), null, "catalog.schema_version"),
                text(value.get("id")), text(value.get("brand")), text(value.get("title")),
                text(value.get("edition_label")), value.get("publication_year") == null ? null
                    : number(value.get("publication_year"), null, "catalog.publication_year"),
                strings(value.get("ranges")), strings(value.get("source_urls")).stream()
                    .map(url -> uri(url, "catalog.source_urls")).toList());
    }

    /** Converts and validates one complete paintable-product import document. */
    public static PaintableProduct product(StructuredDocument document) {
        var value = map(document);
        var edition = map(value.get("edition"));
        var sources = maps(value.get("sources")).stream().map(MarketCatalogFactory::productSource).toList();
        var items = maps(value.get("catalog_items")).stream().map(item -> new PaintableProduct.PaintableComponent(
                text(item.get("id")), text(item.get("product_id")), text(item.get("name")),
                text(item.get("kind")), number(item.get("quantity"), null, "product.catalog_items.quantity"),
                text(item.get("description")), Boolean.TRUE.equals(item.get("assembly_required")),
                maps(item.get("reference_images")).stream().map(image -> new PaintableProduct.ReferenceImage(
                        text(image.get("url")), text(image.get("page_url")), text(image.get("credit")),
                        text(image.get("license")))).toList(),
                maps(item.get("sources")).stream().map(MarketCatalogFactory::productSource).toList())).toList();
        return new PaintableProduct(
                number(value.get("schema_version"), null, "product.schema_version"),
                text(value.get("id")), text(value.get("name")), text(value.get("line")),
                text(value.get("product_type")), text(value.get("scope")),
                number(value.get("expected_paintable_count"), null, "product.expected_paintable_count"),
                new PaintableProduct.Edition(text(edition.get("note")), text(edition.get("url"))), sources, items);
    }

    private static PaintableProduct.Source productSource(Map<String, Object> source) {
        return new PaintableProduct.Source(
                text(source.get("kind")), text(source.get("label")), text(source.get("url")));
    }

    private static PaintProduct paint(StructuredDocument document) {
        var value = map(document);
        var color = map(value.get("color"));
        var profile = map(value.get("profile"));
        var undercoat = map(profile.get("undercoat"));
        var usage = map(value.get("usage_instructions"));
        return new PaintProduct(
                number(value.get("schema_version"), null, "paint.schema_version"),
                text(value.get("id")), text(value.get("brand")), text(value.get("manufacturer")),
                strings(value.get("brand_aliases")), text(value.get("range")),
                new PaintProductProfile(
                        strings(profile.get("roles")).stream().map(PaintProductProfile.Role::fromId).toList(),
                        strings(profile.get("application_methods")).stream()
                                .map(PaintProductProfile.ApplicationMethod::fromId).toList(),
                        PaintProductProfile.ApplicationSystem.fromId(text(profile.get("application_system"))),
                        PaintProductProfile.Coverage.fromId(text(profile.get("coverage"))),
                        PaintProductProfile.Finish.fromId(text(profile.get("finish"))),
                        strings(profile.get("effects")).stream().map(PaintProductProfile.Effect::fromId).toList(),
                        new PaintProductProfile.Undercoat(
                                PaintProductProfile.UndercoatTone.fromId(text(undercoat.get("tone"))),
                                Boolean.TRUE.equals(undercoat.get("pre_highlighted_surface_recommended"))),
                        PaintProductProfile.Medium.fromId(text(profile.get("medium")))),
                text(value.get("reference")), text(value.get("name")),
                new PaintProduct.Color(text(color.get("family")), text(color.get("hex"))),
                PaintProductLifecycle.fromId(defaultText(text(value.get("lifecycle_status")), "unknown")),
                defaultText(text(value.get("data_status")), "unreviewed"),
                strings(value.get("warnings")), strings(value.get("tags")),
                text(value.get("notes")),
                uri(value.get("manufacturer_page"), "paint.manufacturer_page"),
                image(map(value.get("manufacturer_image")), "paint.manufacturer_image"),
                number(value.get("volume_ml"), 0, "paint.volume_ml"),
                strings(value.get("recommended_uses")),
                new PaintProduct.UsageInstructions(
                        text(usage.get("summary")), strings(usage.get("steps")), strings(usage.get("tips")),
                        text(usage.get("instruction_status")), Boolean.TRUE.equals(usage.get("review_required"))),
                date(value.get("verified_at"), "paint.verified_at"),
                image(map(value.get("result_image")), "paint.result_image"),
                maps(value.get("catalog_memberships")).stream().map(m -> new PaintCatalogEdition.Membership(
                        text(m.get("catalog_edition_id")), uri(m.get("source_url"), "membership.source_url"),
                        text(m.get("locator")))).toList(), strings(value.get("usage_guide_ids")));
    }

    private static PaintProduct.ImageReference image(Map<String, Object> value, String field) {
        var limitation = map(value.get("quality_limitation"));
        return new PaintProduct.ImageReference(
                text(value.get("path")), uri(value.get("source_url"), field + ".source_url"),
                text(value.get("credit")), text(value.get("license")),
                uri(value.get("reference_url"), field + ".reference_url"),
                PaintProductImageQuality.fromId(text(value.get("image_quality"))),
                date(value.get("quality_verified_at"), field + ".quality_verified_at"),
                limitation.isEmpty() ? null : new PaintProduct.ImageQualityLimitation(
                        PaintProductImageLimitationCode.fromId(text(limitation.get("code"))),
                        text(limitation.get("detail")),
                        date(limitation.get("observed_at"), field + ".quality_limitation.observed_at")));
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
            List<PaintProduct> paints,
            List<PaintableProduct> products,
            List<MarketPaintingGuide> guides) {
        var paintIds = uniqueIds(paints.stream().map(PaintProduct::id).toList(), "market paint");
        uniqueIds(products.stream().map(PaintableProduct::id).toList(), "paintable product");
        var paintableComponentIds = uniqueIds(products.stream()
                .flatMap(product -> product.paintableComponents().stream()).map(PaintableProduct.PaintableComponent::id).toList(),
                "paintable component");
        var guideVersions = new HashSet<String>();
        for (var guide : guides) {
            if (!guideVersions.add(guide.id() + "@" + guide.version())) {
                throw invalid("Duplicate market painting guide version: " + guide.id() + "@" + guide.version());
            }
            if (!paintableComponentIds.contains(guide.paintableComponentId())) {
                throw invalid("Guide " + guide.id() + " references unknown paintable component " + guide.paintableComponentId());
            }
            for (var slot : guide.slots()) {
                if (slot.paintProductId() != null && !paintIds.contains(slot.paintProductId())) {
                    throw invalid("Guide slot " + slot.id() + " references unknown market paint " + slot.paintProductId());
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
