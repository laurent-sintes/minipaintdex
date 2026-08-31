package com.minipaintdex.application;

import com.minipaintdex.application.document.StructuredDocument;
import com.minipaintdex.application.port.MarketCatalogReader;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.application.query.PageQuery;
import com.minipaintdex.application.query.SortOrder;
import com.minipaintdex.application.result.PageResult;
import com.minipaintdex.application.view.MarketPaintView;
import com.minipaintdex.application.view.PaintFacetValue;
import com.minipaintdex.application.view.PaintFacetsView;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/** Read-only market-paint application service. */
final class MarketPaintQueryService {
    private static final java.util.Set<String> TECHNICAL_TYPES = java.util.Set.of(
            "technical_effect", "primer", "wash_shade", "ink", "auxiliary");
    private final MarketCatalogReader catalogs;

    MarketPaintQueryService(MarketCatalogReader catalogs) {
        this.catalogs = Objects.requireNonNull(catalogs);
    }

    List<MarketPaintView> search(SearchMarketPaintsQuery filters) {
        return stream(filters)
                .sorted(Comparator.comparing(MarketPaintView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    Stream<MarketPaintView> stream(SearchMarketPaintsQuery filters) {
        var snapshot = catalogs.load();
        var query = text(filters.query()).toLowerCase(Locale.ROOT);
        return snapshot.paints().stream().map(MarketPaintQueryService::documentMap)
                .map(MarketPaintQueryService::view).filter(paint -> {
            if (!matches(filters.brand(), paint.brand())) return false;
            if (!matches(filters.range(), paint.range())) return false;
            if (!matches(filters.type(), paint.paintType())) return false;
            if (!matches(filters.color(), paint.colorFamily())) return false;
            if (!matches(filters.finish(), paint.finish())) return false;
            if (!matches(filters.medium(), paint.medium())) return false;
            if (!matches(filters.opacity(), paint.opacity())) return false;
            if (!matches(filters.volume(), paint.volumeMl())) return false;
            if (!matches(filters.reference(), paint.reference())) return false;
            if (!matches(filters.lifecycle(), paint.lifecycleStatus())) return false;
            if (!matches(filters.manufacturer(), paint.manufacturer())) return false;
            if (present(filters.tag()) && paint.tags().stream().noneMatch(tag -> tag.equalsIgnoreCase(filters.tag()))) return false;
            if (!query.isBlank()) {
                var haystack = String.join(" ", paint.name(), paint.brand(), paint.manufacturer(), paint.range(),
                        paint.reference(), String.join(" ", paint.tags())).toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) return false;
            }
            return true;
        });
    }

    PageResult<MarketPaintView> page(
            SearchMarketPaintsQuery filters, boolean manufacturerSheetOnly,
            boolean realResultOnly, PageQuery page) {
        var filtered = search(filters).stream()
                .filter(paint -> !manufacturerSheetOnly || present(paint.manufacturerUrl()))
                .filter(paint -> !realResultOnly || present(paint.resultImage()) || present(paint.resultReferenceUrl()))
                .sorted(comparator(page.sort()))
                .toList();
        var from = Math.min(page.offset(), filtered.size());
        var to = Math.min(from + page.size(), filtered.size());
        return new PageResult<>(filtered.subList(from, to), page.page(), page.size(), filtered.size());
    }

    private static Comparator<MarketPaintView> comparator(List<SortOrder> orders) {
        var effective = orders.isEmpty() ? List.of(new SortOrder("name", SortOrder.Direction.ASCENDING)) : orders;
        Comparator<MarketPaintView> comparator = null;
        for (var order : effective) {
            if (!java.util.Set.of("name", "brand", "range", "reference", "paintType", "colorFamily").contains(order.property())) {
                throw new com.minipaintdex.domain.shared.DomainException(
                        "invalid_input", "Unsupported paint sort property: " + order.property());
            }
            Comparator<MarketPaintView> next = Comparator.comparing(
                    value -> sortableValue(value, order.property()), String.CASE_INSENSITIVE_ORDER);
            if (order.direction() == SortOrder.Direction.DESCENDING) next = next.reversed();
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        return comparator.thenComparing(MarketPaintView::id, String.CASE_INSENSITIVE_ORDER);
    }

    PaintFacetsView facets() {
        var paints = views(catalogs.load());
        return new PaintFacetsView(
                paints.size(),
                facet(paints, paint -> List.of(paint.paintType())),
                facet(paints, paint -> List.of(paint.colorFamily())),
                facet(paints, paint -> List.of(paint.brand())),
                facet(paints, paint -> List.of(paint.manufacturer())),
                facet(paints, paint -> List.of(paint.range())),
                facet(paints, paint -> List.of(paint.finish())),
                facet(paints, paint -> List.of(paint.medium())),
                facet(paints, paint -> List.of(paint.opacity())),
                facet(paints, paint -> List.of(paint.lifecycleStatus())),
                facet(paints, paint -> paint.volumeMl() > 0 ? List.of(paint.volumeMl() + " ml") : List.of()),
                facet(paints, MarketPaintView::tags));
    }

    List<MarketPaintView> views(MarketCatalogSnapshot snapshot) {
        return snapshot.paints().stream().map(MarketPaintQueryService::documentMap)
                .map(MarketPaintQueryService::view).sorted(
                Comparator.comparing(MarketPaintView::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static MarketPaintView view(Map<String, Object> entry) {
        var color = map(entry.get("color"));
        var manufacturerImage = map(entry.get("manufacturer_image"));
        var resultImage = map(entry.get("result_image"));
        var verifiedAt = text(entry.get("verified_at"));
        var usage = map(entry.get("usage_instructions"));
        var instructionStatus = text(usage.get("instruction_status"));
        var technical = TECHNICAL_TYPES.contains(text(entry.get("functional_type")));
        return new MarketPaintView(
                text(entry.get("id")), text(entry.get("brand")), text(entry.get("manufacturer")),
                strings(entry.get("brand_aliases")), text(entry.get("range")), text(entry.get("functional_type")),
                text(entry.get("reference")), text(entry.get("name")), text(color.get("hex")),
                text(entry.get("finish")), text(entry.get("medium")), text(entry.get("opacity")),
                text(entry.get("lifecycle_status")),
                text(entry.get("data_status")), String.join(" · ", strings(entry.get("warnings"))),
                strings(entry.get("tags")), text(entry.get("notes")),
                verifiedAt.isBlank() ? "" : verifiedAt + "T00:00:00.000Z",
                verifiedAt.isBlank() ? "" : verifiedAt + "T00:00:00.000Z",
                text(entry.get("manufacturer_page")), text(manufacturerImage.get("path")),
                text(manufacturerImage.get("credit")), number(entry.get("volume_ml")), text(color.get("family")),
                text(entry.get("notes")), strings(entry.get("recommended_uses")),
                new MarketPaintView.UsageInstructions(
                        text(usage.get("summary")), strings(usage.get("steps")), strings(usage.get("tips")),
                        instructionStatus.isBlank() && technical ? "legacy_unverified" : instructionStatus,
                        Boolean.TRUE.equals(usage.get("review_required")) || (technical && instructionStatus.isBlank())),
                verifiedAt, text(resultImage.get("path")), text(resultImage.get("credit")),
                text(resultImage.get("source_url")), text(resultImage.get("license")),
                text(resultImage.get("reference_url")));
    }

    private static List<PaintFacetValue> facet(List<MarketPaintView> paints, Function<MarketPaintView, List<String>> values) {
        var counts = new java.util.TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
        paints.forEach(paint -> values.apply(paint).stream().filter(MarketPaintQueryService::present)
                .forEach(value -> counts.merge(value, 1, Integer::sum)));
        return counts.entrySet().stream().map(entry -> new PaintFacetValue(entry.getKey(), entry.getValue())).toList();
    }

    private static String sortableValue(MarketPaintView value, String property) {
        return switch (property) {
            case "name" -> value.name();
            case "brand" -> value.brand();
            case "range" -> value.range();
            case "reference" -> value.reference();
            case "paintType" -> value.paintType();
            case "colorFamily" -> value.colorFamily();
            default -> throw new IllegalArgumentException("Unsupported paint sort property: " + property);
        };
    }

    private static boolean matches(String expected, Object actual) {
        return !present(expected) || expected.equalsIgnoreCase(text(actual));
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value).replace(" ml", "")); } catch (NumberFormatException ignored) { return 0; }
    }

    private static Map<String, Object> documentMap(StructuredDocument document) {
        var result = new LinkedHashMap<String, Object>();
        document.fields().forEach(field -> result.put(field.name(), documentValue(field.value())));
        return result;
    }

    private static Object documentValue(StructuredDocument.Value value) {
        return switch (value) {
            case StructuredDocument.Text text -> text.value();
            case StructuredDocument.NumberValue number -> number.value();
            case StructuredDocument.BooleanValue bool -> bool.value();
            case StructuredDocument.NullValue ignored -> null;
            case StructuredDocument.ArrayValue array -> array.values().stream()
                    .map(MarketPaintQueryService::documentValue).toList();
            case StructuredDocument.ObjectValue object -> documentMap(object.value());
        };
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of(); }
    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) return list.stream().map(MarketPaintQueryService::text).filter(MarketPaintQueryService::present).toList();
        return present(text(value)) ? List.of(text(value)) : List.of();
    }
}
