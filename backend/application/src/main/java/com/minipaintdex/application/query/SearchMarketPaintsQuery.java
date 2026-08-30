package com.minipaintdex.application.query;

public record SearchMarketPaintsQuery(
        String query,
        String brand,
        String range,
        String type,
        String color,
        String finish,
        String medium,
        String opacity,
        String volume,
        String reference,
        String lifecycle,
        String manufacturer,
        String tag) {

    public static SearchMarketPaintsQuery empty() {
        return new SearchMarketPaintsQuery(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
