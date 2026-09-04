package com.minipaintdex.application.validation;

import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.domain.market.product.PaintableProduct;
import com.minipaintdex.domain.shared.DomainException;
import com.minipaintdex.domain.workshop.PaintingProjectProjector;
import com.minipaintdex.domain.workshop.WorkshopPaintableProjector;
import com.minipaintdex.domain.workshop.WorkshopPaintInventory;
import com.minipaintdex.domain.workshop.WorkshopProjector;
import com.minipaintdex.domain.workshop.WorkshopRecipeProjector;
import com.minipaintdex.domain.workshop.WorkshopShoppingPlan;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Validates one complete persistence generation, including cross-context references. */
public final class DataSnapshotValidator {
    private DataSnapshotValidator() {}

    public static ValidatedSnapshot validate(DataSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot is required");
        var catalog = MarketCatalogFactory.create(
                snapshot.paintProducts(), snapshot.paintableProducts(), snapshot.marketPaintingGuides(), snapshot.paintCatalogEditions(), snapshot.paintUsageGuides());
        var inventory = inventory(snapshot);
        var shopping = shopping(snapshot);
        validateReferences(snapshot, catalog, inventory, shopping);
        validateStorageReferences(snapshot);
        return new ValidatedSnapshot(catalog, inventory, shopping);
    }
    private static void validateStorageReferences(DataSnapshot snapshot) {
        var formats = snapshot.rackCatalog().containerFormats().stream().map(value -> value.id()).collect(Collectors.toSet());
        snapshot.paintProducts().forEach(document -> {
            var id = StructuredDocuments.text(StructuredDocuments.toMap(document).get("container_format_id"));
            requireReference(formats.contains(id), "Paint product container", id);
        });
        var pots = com.minipaintdex.domain.workshop.PaintPotProjector.project(snapshot.events()).stream().collect(Collectors.toMap(value -> value.id(), value -> value));
        pots.values().forEach(pot -> {
            if (pot.containerIdentification() != null && pot.containerIdentification().containerFormatId() != null)
                requireReference(formats.contains(pot.containerIdentification().containerFormatId()), "Paint pot container", pot.containerIdentification().containerFormatId());
        });
        var racks = com.minipaintdex.application.storage.StorageProjection.racks(snapshot);
        racks.forEach(rack -> com.minipaintdex.application.storage.StorageProjection.validateConfiguration(rack.configuration(), snapshot));
        var rackIds = racks.stream().map(value -> value.id()).collect(Collectors.toSet());
        com.minipaintdex.application.storage.StorageProjection.storage(snapshot).placements().forEach(placement -> {
            requireReference(pots.containsKey(placement.paintPotId()), "Placement pot", placement.paintPotId());
            requireReference(rackIds.contains(placement.workshopRackId()), "Placement rack", placement.workshopRackId());
        });
    }

    private static WorkshopPaintInventory inventory(DataSnapshot snapshot) {
        return snapshot.paintInventory();
    }

    private static WorkshopShoppingPlan shopping(DataSnapshot snapshot) {
        return new WorkshopShoppingPlan(StructuredDocuments.toMaps(snapshot.shopping()).stream()
                .map(entry -> new WorkshopShoppingPlan.PaintPurchaseIntent(
                        StructuredDocuments.text(entry.get("id")),
                        StructuredDocuments.text(entry.get("market_paint_id")),
                        StructuredDocuments.text(entry.get("brand")),
                        StructuredDocuments.text(entry.get("name")),
                        StructuredDocuments.text(entry.get("reference")),
                        StructuredDocuments.text(entry.get("color_hex")),
                        StructuredDocuments.text(entry.get("reason")),
                        WorkshopShoppingPlan.Priority.fromId(defaultText(
                                StructuredDocuments.text(entry.get("priority")), "low"))))
                .toList());
    }

    private static void validateReferences(
            DataSnapshot snapshot,
            MarketCatalogSnapshot catalog,
            WorkshopPaintInventory inventory,
            WorkshopShoppingPlan shopping) {
        var paintProductIds = catalog.paints().stream().map(paint -> paint.id()).collect(Collectors.toSet());
        com.minipaintdex.domain.workshop.PaintPotProjector.project(snapshot.events()).forEach(pot ->
                requireReference(paintProductIds.contains(pot.paintProductId()), "Paint pot", pot.paintProductId()));
        inventory.stocks().forEach(stock -> requireReference(
                paintProductIds.contains(stock.paintProductId()), "Workshop inventory", stock.paintProductId()));
        shopping.intents().stream().map(WorkshopShoppingPlan.PaintPurchaseIntent::paintProductId).filter(Objects::nonNull)
                .forEach(id -> requireReference(paintProductIds.contains(id), "Shopping plan", id));

        var products = catalog.paintableProducts().stream()
                .collect(Collectors.toMap(PaintableProduct::id, Function.identity()));
        var paintableComponents = catalog.paintableProducts().stream().flatMap(product -> product.paintableComponents().stream())
                .collect(Collectors.toMap(PaintableProduct.PaintableComponent::id, Function.identity()));
        var projects = PaintingProjectProjector.project(snapshot.events());
        var projectsById = projects.stream().collect(Collectors.toMap(project -> project.id(), Function.identity()));
        projects.forEach(project -> requireReference(
                products.containsKey(project.paintableProductId()), "Painting project", project.paintableProductId()));
        var workshop = WorkshopProjector.project(snapshot.events());
        if (!projects.isEmpty() && workshop.isEmpty()) {
            throw invalid("Painting projects require the workshop aggregate.");
        }
        projects.forEach(project -> requireReference(
                workshop.orElseThrow().containsPaintingProject(project.id()), "Workshop", project.id()));

        var recipes = WorkshopRecipeProjector.project(snapshot.events());
        var recipesById = recipes.stream().collect(Collectors.toMap(recipe -> recipe.id(), Function.identity()));
        recipes.forEach(recipe -> requireReference(
                paintableComponents.containsKey(recipe.paintableComponentId()), "Workshop recipe", recipe.paintableComponentId()));
        WorkshopPaintableProjector.project(snapshot.events()).forEach(item -> {
            var project = projectsById.get(item.paintingProjectId());
            requireReference(project != null, "Workshop paintable", item.paintingProjectId());
            var paintableComponent = paintableComponents.get(item.paintableComponentId());
            requireReference(paintableComponent != null, "Workshop paintable", item.paintableComponentId());
            requireReference(project.paintableProductId().equals(paintableComponent.paintableProductId()),
                    "Workshop paintable product", paintableComponent.paintableProductId());
            if (item.recipeId() != null) {
                var recipe = recipesById.get(item.recipeId());
                requireReference(recipe != null, "Workshop paintable recipe", item.recipeId());
                requireReference(recipe.paintableComponentId().equals(item.paintableComponentId()),
                        "Workshop paintable recipe paintable component", recipe.paintableComponentId());
            }
        });
    }

    private static void requireReference(boolean condition, String owner, String target) {
        if (!condition) throw invalid(owner + " references unknown or incompatible id: " + target);
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static DomainException invalid(String message) {
        return new DomainException("invalid_data_snapshot", message);
    }

    public record ValidatedSnapshot(
            MarketCatalogSnapshot market,
            WorkshopPaintInventory inventory,
            WorkshopShoppingPlan shopping) {}
}
