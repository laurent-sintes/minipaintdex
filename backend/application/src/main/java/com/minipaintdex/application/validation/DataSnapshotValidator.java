package com.minipaintdex.application.validation;

import com.minipaintdex.application.port.DataSnapshot;
import com.minipaintdex.application.port.MarketCatalogSnapshot;
import com.minipaintdex.domain.market.product.PaintableProduct;
import com.minipaintdex.domain.shared.DomainException;
import com.minipaintdex.domain.workshop.PaintingProjectProjector;
import com.minipaintdex.domain.workshop.WorkshopItemProjector;
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
                snapshot.marketPaints(), snapshot.paintableProducts(), snapshot.marketPaintingGuides(), snapshot.paintCatalogEditions());
        var inventory = inventory(snapshot);
        var shopping = shopping(snapshot);
        validateReferences(snapshot, catalog, inventory, shopping);
        return new ValidatedSnapshot(catalog, inventory, shopping);
    }

    private static WorkshopPaintInventory inventory(DataSnapshot snapshot) {
        return new WorkshopPaintInventory(StructuredDocuments.toMaps(snapshot.paintInventory()).stream()
                .map(entry -> new WorkshopPaintInventory.Stock(
                        StructuredDocuments.text(entry.get("paint_id")),
                        StructuredDocuments.integer(entry.get("quantity"), "paint_inventory.quantity")))
                .toList());
    }

    private static WorkshopShoppingPlan shopping(DataSnapshot snapshot) {
        return new WorkshopShoppingPlan(StructuredDocuments.toMaps(snapshot.shopping()).stream()
                .map(entry -> new WorkshopShoppingPlan.Intent(
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
        var marketPaintIds = catalog.paints().stream().map(paint -> paint.id()).collect(Collectors.toSet());
        inventory.stocks().forEach(stock -> requireReference(
                marketPaintIds.contains(stock.marketPaintId()), "Workshop inventory", stock.marketPaintId()));
        shopping.intents().stream().map(WorkshopShoppingPlan.Intent::marketPaintId).filter(Objects::nonNull)
                .forEach(id -> requireReference(marketPaintIds.contains(id), "Shopping plan", id));

        var products = catalog.paintableProducts().stream()
                .collect(Collectors.toMap(PaintableProduct::id, Function.identity()));
        var catalogItems = catalog.paintableProducts().stream().flatMap(product -> product.catalogItems().stream())
                .collect(Collectors.toMap(PaintableProduct.CatalogItem::id, Function.identity()));
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
                catalogItems.containsKey(recipe.catalogItemId()), "Workshop recipe", recipe.catalogItemId()));
        WorkshopItemProjector.project(snapshot.events()).forEach(item -> {
            var project = projectsById.get(item.paintingProjectId());
            requireReference(project != null, "Workshop item", item.paintingProjectId());
            var catalogItem = catalogItems.get(item.catalogItemId());
            requireReference(catalogItem != null, "Workshop item", item.catalogItemId());
            requireReference(project.paintableProductId().equals(catalogItem.productId()),
                    "Workshop item product", catalogItem.productId());
            if (item.recipeId() != null) {
                var recipe = recipesById.get(item.recipeId());
                requireReference(recipe != null, "Workshop item recipe", item.recipeId());
                requireReference(recipe.catalogItemId().equals(item.catalogItemId()),
                        "Workshop item recipe catalog item", recipe.catalogItemId());
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
