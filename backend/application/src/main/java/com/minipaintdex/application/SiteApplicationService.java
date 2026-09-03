package com.minipaintdex.application;

import com.minipaintdex.application.port.SnapshotRepository;
import com.minipaintdex.application.usecase.SiteQueries;
import com.minipaintdex.application.validation.MarketCatalogFactory;
import com.minipaintdex.application.validation.StructuredDocuments;
import com.minipaintdex.application.view.DashboardView;
import com.minipaintdex.application.view.SiteConfigurationView;
import com.minipaintdex.domain.workshop.PaintingProjectProjector;
import com.minipaintdex.domain.workshop.WorkshopPaintableProjector;
import com.minipaintdex.domain.workshop.WorkshopPaintableState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-only application service for localized site configuration and dashboard counters. */
public final class SiteApplicationService implements SiteQueries {
    private final SnapshotRepository snapshots;

    public SiteApplicationService(SnapshotRepository snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots);
    }

    @Override
    public SiteConfigurationView siteConfiguration() {
        return new SiteConfigurationView(configurationSection(
                StructuredDocuments.toMap(snapshots.load().site()), true));
    }

    @Override
    public DashboardView dashboard() {
        var snapshot = snapshots.load();
        var catalog = MarketCatalogFactory.create(
                snapshot.paintProducts(), snapshot.paintableProducts(), snapshot.marketPaintingGuides(), snapshot.paintCatalogEditions(), snapshot.paintUsageGuides());
        var items = WorkshopPaintableProjector.project(snapshot.events());
        var projects = PaintingProjectProjector.project(snapshot.events());
        var ownedPaintIds = snapshot.paintInventory().ownedPaintProductIds();
        var completedItems = items.stream().filter(WorkshopPaintableState::completed).count();
        return new DashboardView(
                new DashboardView.PaintStats(
                        catalog.paints().size(), ownedPaintIds.size(),
                        catalog.paints().stream().map(paint -> paint.brand()).distinct().count()),
                catalog.paintableProducts().size(),
                new DashboardView.WorkshopStats(
                        projects.size(), items.size(), completedItems,
                        items.isEmpty() ? 0 : Math.round(completedItems * 100f / items.size())));
    }

    private static SiteConfigurationView.Section configurationSection(
            Map<String, Object> values, boolean normalizeKeys) {
        return new SiteConfigurationView.Section(values.entrySet().stream()
                .map(entry -> {
                    var key = normalizeKeys ? camelKey(entry.getKey()) : entry.getKey();
                    var normalizeChildren = normalizeKeys && !Set.of(
                            "workflow", "kind_labels", "event_labels", "document_titles").contains(entry.getKey());
                    return new SiteConfigurationView.Entry(
                            key, configurationValue(entry.getValue(), normalizeChildren));
                }).toList());
    }

    private static SiteConfigurationView.Value configurationValue(Object value, boolean normalizeKeys) {
        if (value instanceof Map<?, ?> values) {
            var typed = new LinkedHashMap<String, Object>();
            values.forEach((key, entry) -> typed.put(String.valueOf(key), entry));
            return configurationSection(typed, normalizeKeys);
        }
        if (value instanceof java.util.List<?> values) {
            return new SiteConfigurationView.ListValue(values.stream()
                    .map(entry -> configurationValue(entry, normalizeKeys)).toList());
        }
        if (value instanceof Number number) return new SiteConfigurationView.NumberValue(number);
        if (value instanceof Boolean bool) return new SiteConfigurationView.BooleanValue(bool);
        return new SiteConfigurationView.TextValue(StructuredDocuments.text(value));
    }

    private static String camelKey(String value) {
        if (value.contains(".")) return value;
        var result = new StringBuilder();
        var upper = false;
        for (var character : value.toCharArray()) {
            if (character == '_') {
                upper = true;
                continue;
            }
            result.append(upper ? Character.toUpperCase(character) : character);
            upper = false;
        }
        return result.toString();
    }
}
