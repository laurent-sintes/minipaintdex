package com.minipaintdex.domain.market.guide;

import com.minipaintdex.domain.shared.DomainException;

import java.net.URI;
import java.util.HashSet;
import java.util.List;

/** Versioned aggregate root for sourced Market painting knowledge targeting one paintable component. */
public record MarketPaintingGuide(
        int schemaVersion,
        String id,
        int version,
        KnowledgeStatus knowledgeStatus,
        String paintableComponentId,
        List<String> sourceReferences,
        Provenance provenance,
        List<Source> sources,
        List<Slot> slots,
        List<Step> preparation,
        List<Step> painting) {

    public MarketPaintingGuide {
        if (schemaVersion != 1) throw invalid("schemaVersion must be 1.");
        id = stableId(id, "id");
        if (version < 1) throw invalid("version must be positive.");
        if (knowledgeStatus == null) throw invalid("knowledgeStatus is required.");
        paintableComponentId = stableId(paintableComponentId, "paintableComponentId");
        sourceReferences = strings(sourceReferences);
        provenance = provenance == null ? new Provenance(null, true) : provenance;
        sources = sources == null ? List.of() : List.copyOf(sources);
        slots = slots == null ? List.of() : List.copyOf(slots);
        preparation = preparation == null ? List.of() : List.copyOf(preparation);
        painting = painting == null ? List.of() : List.copyOf(painting);
        if (slots.isEmpty()) throw invalid("At least one painting-guide slot is required.");
        var slotIds = new HashSet<String>();
        for (var slot : slots) if (!slotIds.add(slot.id())) throw invalid("Duplicate slot id: " + slot.id());
        if (sourceReferences.isEmpty() && sources.isEmpty()) {
            throw invalid("A painting guide requires source references or direct sources.");
        }
    }

    public enum KnowledgeStatus {
        DOCUMENTED("documented"), OBSERVED("observed"), INFERRED("inferred");

        private final String id;
        KnowledgeStatus(String id) { this.id = id; }
        public String id() { return id; }
        public static KnowledgeStatus fromId(String id) {
            for (var value : values()) if (value.id.equals(id)) return value;
            throw invalid("Unknown knowledge status: " + id);
        }
    }

    public record Provenance(String method, boolean reviewRequired) {
        public Provenance { method = optional(method); }
    }

    public record Source(String kind, String label, URI url) {
        public Source {
            kind = required(kind, "source.kind");
            label = required(label, "source.label");
        }
    }

    public record Slot(
            String id,
            String role,
            String paintProductId,
            boolean pendingImport,
            RequestedPaint requestedPaint) {
        public Slot {
            id = stableId(id, "slot.id");
            role = required(role, "slot.role");
            paintProductId = optionalId(paintProductId, "slot.paintProductId");
            requestedPaint = requestedPaint == null ? RequestedPaint.empty() : requestedPaint;
            if (paintProductId == null && !pendingImport) {
                throw invalid("Slot " + id + " must reference a market paint or be pending import.");
            }
        }
    }

    public record RequestedPaint(String brand, String name, String colorHex) {
        public RequestedPaint {
            brand = optional(brand);
            name = optional(name);
            colorHex = optional(colorHex);
            if (colorHex != null && !colorHex.matches("#[0-9A-Fa-f]{6}")) {
                throw invalid("requestedPaint.colorHex must use #RRGGBB.");
            }
        }
        public static RequestedPaint empty() { return new RequestedPaint(null, null, null); }
    }

    public record Step(String title, String detail) {
        public Step {
            title = required(title, "step.title");
            detail = required(detail, "step.detail");
        }
    }

    private static List<String> strings(List<String> values) {
        if (values == null) return List.of();
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw invalid("String collections cannot contain blank values.");
        }
        return List.copyOf(values);
    }

    private static String stableId(String value, String field) {
        var result = required(value, field);
        if (!result.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw invalid(field + " must use kebab-case.");
        return result;
    }

    private static String optionalId(String value, String field) {
        return value == null || value.isBlank() ? null : stableId(value, field);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " is required.");
        return value;
    }

    private static String optional(String value) { return value == null || value.isBlank() ? null : value; }
    private static DomainException invalid(String message) {
        return new DomainException("invalid_market_painting_guide", message);
    }
}
