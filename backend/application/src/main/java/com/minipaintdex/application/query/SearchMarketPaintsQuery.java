package com.minipaintdex.application.query;

public record SearchMarketPaintsQuery(
        String query,
        String brand,
        String range,
        String role,
        String applicationMethod,
        String applicationSystem,
        String color,
        String finish,
        String medium,
        String coverage,
        String effect,
        String undercoat,
        String lifecycle) {

    public static SearchMarketPaintsQuery empty() {
        return new SearchMarketPaintsQuery(
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
