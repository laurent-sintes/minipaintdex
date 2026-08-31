package com.minipaintdex.application;

import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.query.SearchMarketPaintsQuery;
import com.minipaintdex.domain.workflow.DomainException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only market-paint application service. */
final class MarketPaintQueryService {
    private static final java.util.Set<String> TECHNICAL_TYPES = java.util.Set.of(
            "technical_effect", "primer", "wash_shade", "ink", "auxiliary");
    private final SnapshotRepository snapshots;

    MarketPaintQueryService(SnapshotRepository snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots);
    }

    List<Map<String, Object>> search(SearchMarketPaintsQuery filters) {
        var paints = views(snapshots.load());
        var query = text(filters.query()).toLowerCase(Locale.ROOT);
        return paints.stream().filter(paint -> {
            if (!matches(filters.brand(), paint.get("brand"))) return false;
            if (!matches(filters.range(), paint.get("range"))) return false;
            if (!matches(filters.type(), paint.get("paintType"))) return false;
            if (!matches(filters.color(), paint.get("colorFamily"))) return false;
            if (!matches(filters.finish(), paint.get("finish"))) return false;
            if (!matches(filters.medium(), paint.get("medium"))) return false;
            if (!matches(filters.opacity(), paint.get("opacity"))) return false;
            if (!matches(filters.volume(), paint.get("volumeMl"))) return false;
            if (!matches(filters.reference(), paint.get("reference"))) return false;
            if (!matches(filters.lifecycle(), paint.get("lifecycleStatus"))) return false;
            if (!matches(filters.manufacturer(), paint.get("manufacturer"))) return false;
            if (present(filters.tag()) && strings(paint.get("tags")).stream().noneMatch(tag -> tag.equalsIgnoreCase(filters.tag()))) return false;
            if (!query.isBlank()) {
                var haystack = String.join(" ", text(paint.get("name")), text(paint.get("brand")),
                        text(paint.get("manufacturer")), text(paint.get("range")), text(paint.get("reference")),
                        text(paint.get("tags"))).toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) return false;
            }
            return true;
        }).toList();
    }

    Map<String, Object> page(
            SearchMarketPaintsQuery filters, boolean ownedOnly, boolean manufacturerSheetOnly,
            boolean realResultOnly, int offset, int limit) {
        if (offset < 0) throw new DomainException("invalid_input", "offset cannot be negative.");
        if (limit < 1 || limit > 200) throw new DomainException("invalid_input", "limit must be between 1 and 200.");
        var filtered = search(filters).stream()
                .filter(paint -> !ownedOnly || number(paint.get("quantity")) > 0)
                .filter(paint -> !manufacturerSheetOnly || present(text(paint.get("manufacturerUrl"))))
                .filter(paint -> !realResultOnly || present(text(paint.get("resultImage"))) || present(text(paint.get("resultReferenceUrl"))))
                .toList();
        var from = Math.min(offset, filtered.size());
        var to = Math.min(from + limit, filtered.size());
        return Map.of("paints", filtered.subList(from, to), "total", filtered.size(), "offset", offset, "limit", limit);
    }

    Map<String, Object> facets(boolean ownedOnly) {
        var paints = views(snapshots.load()).stream()
                .filter(paint -> !ownedOnly || number(paint.get("quantity")) > 0).toList();
        var facets = new LinkedHashMap<String, Object>();
        facets.put("types", facet(paints, paint -> List.of(text(paint.get("paintType")))));
        facets.put("colors", facet(paints, paint -> List.of(text(paint.get("colorFamily")))));
        facets.put("brands", facet(paints, paint -> List.of(text(paint.get("brand")))));
        facets.put("manufacturers", facet(paints, paint -> List.of(text(paint.get("manufacturer")))));
        facets.put("ranges", facet(paints, paint -> List.of(text(paint.get("range")))));
        facets.put("finishes", facet(paints, paint -> List.of(text(paint.get("finish")))));
        facets.put("mediums", facet(paints, paint -> List.of(text(paint.get("medium")))));
        facets.put("opacities", facet(paints, paint -> List.of(text(paint.get("opacity")))));
        facets.put("lifecycles", facet(paints, paint -> List.of(text(paint.get("lifecycleStatus")))));
        facets.put("volumes", facet(paints, paint -> number(paint.get("volumeMl")) > 0 ? List.of(number(paint.get("volumeMl")) + " ml") : List.of()));
        facets.put("tags", facet(paints, paint -> strings(paint.get("tags"))));
        return Map.of("total", paints.size(), "facets", Map.copyOf(facets));
    }

    List<Map<String, Object>> views(DataSnapshot snapshot) {
        var quantities = snapshot.paintInventory().stream().collect(Collectors.toMap(
                entry -> text(entry.get("paint_id")), entry -> number(entry.get("quantity")), Integer::sum));
        return snapshot.marketPaints().stream().map(entry -> view(entry, quantities)).sorted(
                Comparator.comparing(entry -> text(entry.get("name")), String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static Map<String, Object> view(Map<String, Object> entry, Map<String, Integer> quantities) {
        var color = map(entry.get("color"));
        var manufacturerImage = map(entry.get("manufacturer_image"));
        var resultImage = map(entry.get("result_image"));
        var verifiedAt = text(entry.get("verified_at"));
        var usage = map(entry.get("usage_instructions"));
        Map<String, Object> paint = new LinkedHashMap<>();
        paint.put("id", text(entry.get("id")));
        paint.put("brand", text(entry.get("brand")));
        paint.put("manufacturer", text(entry.get("manufacturer")));
        paint.put("brandAliases", strings(entry.get("brand_aliases")));
        paint.put("range", text(entry.get("range")));
        paint.put("paintType", text(entry.get("functional_type")));
        paint.put("reference", text(entry.get("reference")));
        paint.put("name", text(entry.get("name")));
        paint.put("colorHex", text(color.get("hex")));
        paint.put("finish", text(entry.get("finish")));
        paint.put("medium", text(entry.get("medium")));
        paint.put("opacity", text(entry.get("opacity")));
        paint.put("lifecycleStatus", text(entry.get("lifecycle_status")));
        paint.put("quantity", quantities.getOrDefault(text(entry.get("id")), 0));
        paint.put("status", text(entry.get("data_status")));
        paint.put("warnings", String.join(" · ", strings(entry.get("warnings"))));
        paint.put("tags", strings(entry.get("tags")));
        paint.put("notes", text(entry.get("notes")));
        paint.put("createdAt", verifiedAt.isBlank() ? "" : verifiedAt + "T00:00:00.000Z");
        paint.put("updatedAt", verifiedAt.isBlank() ? "" : verifiedAt + "T00:00:00.000Z");
        paint.put("manufacturerUrl", text(entry.get("manufacturer_page")));
        paint.put("manufacturerImage", text(manufacturerImage.get("path")));
        paint.put("manufacturerImageCredit", text(manufacturerImage.get("credit")));
        paint.put("volumeMl", number(entry.get("volume_ml")));
        paint.put("colorFamily", text(color.get("family")));
        paint.put("manufacturerDescription", text(entry.get("notes")));
        paint.put("recommendedUses", strings(entry.get("recommended_uses")));
        var instructions = new LinkedHashMap<String, Object>();
        instructions.put("summary", text(usage.get("summary")));
        instructions.put("steps", strings(usage.get("steps")));
        instructions.put("tips", strings(usage.get("tips")));
        var instructionStatus = text(usage.get("instruction_status"));
        var technical = TECHNICAL_TYPES.contains(text(entry.get("functional_type")));
        instructions.put("instructionStatus", instructionStatus.isBlank() && technical ? "legacy_unverified" : instructionStatus);
        instructions.put("reviewRequired", Boolean.TRUE.equals(usage.get("review_required")) || (technical && instructionStatus.isBlank()));
        paint.put("usageInstructions", Map.copyOf(instructions));
        paint.put("manufacturerVerifiedAt", verifiedAt);
        paint.put("resultImage", text(resultImage.get("path")));
        paint.put("resultImageCredit", text(resultImage.get("credit")));
        paint.put("resultImageSource", text(resultImage.get("source_url")));
        paint.put("resultImageLicense", text(resultImage.get("license")));
        paint.put("resultReferenceUrl", text(resultImage.get("reference_url")));
        return paint;
    }

    private static List<Map<String, Object>> facet(List<Map<String, Object>> paints, Function<Map<String, Object>, List<String>> values) {
        var counts = new java.util.TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
        paints.forEach(paint -> values.apply(paint).stream().filter(MarketPaintQueryService::present)
                .forEach(value -> counts.merge(value, 1, Integer::sum)));
        return counts.entrySet().stream().map(entry -> Map.<String, Object>of("value", entry.getKey(), "count", entry.getValue())).toList();
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
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of(); }
    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) return list.stream().map(MarketPaintQueryService::text).filter(MarketPaintQueryService::present).toList();
        return present(text(value)) ? List.of(text(value)) : List.of();
    }
}
