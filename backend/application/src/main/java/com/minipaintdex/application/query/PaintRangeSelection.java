package com.minipaintdex.application.query;

import com.minipaintdex.domain.shared.DomainException;

/** A brand-qualified range, so identically named ranges never select another brand. */
public record PaintRangeSelection(String brand, String range) {
    public PaintRangeSelection {
        if (brand == null || brand.isBlank() || range == null || range.isBlank()) {
            throw new DomainException("invalid_input", "A range selection requires a brand and range.");
        }
    }

    /** Selection syntax: brand::range; escape literal colons and backslashes with a backslash. */
    public String selectionKey() { return escape(brand) + "::" + escape(range); }

    public static PaintRangeSelection parse(String value) {
        if (value == null) throw invalid();
        var parts = new java.util.ArrayList<String>();
        var part = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            var c = value.charAt(i);
            if (c == '\\') {
                if (++i == value.length()) throw invalid();
                part.append(value.charAt(i));
            } else if (c == ':' && i + 1 < value.length() && value.charAt(i + 1) == ':') {
                parts.add(part.toString());
                part.setLength(0);
                i++;
            } else part.append(c);
        }
        parts.add(part.toString());
        if (parts.size() != 2) throw invalid();
        return new PaintRangeSelection(parts.get(0), parts.get(1));
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace(":", "\\:"); }
    private static DomainException invalid() {
        return new DomainException("invalid_input", "Range selections must use brand::range.");
    }
}
