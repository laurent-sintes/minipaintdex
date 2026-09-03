package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.shared.DomainException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable owner-stock value set referencing Market paint identities. */
public record WorkshopPaintInventory(List<WorkshopPaintStock> stocks) {
    public WorkshopPaintInventory {
        stocks = stocks == null ? List.of() : List.copyOf(stocks);
        var ids = new HashSet<String>();
        for (var stock : stocks) {
            if (stock == null) throw invalid("Paint inventory cannot contain null entries.");
            if (!ids.add(stock.paintProductId())) {
                throw invalid("Duplicate workshop paint: " + stock.paintProductId());
            }
        }
    }

    public static WorkshopPaintInventory fromPots(List<PaintPot> pots) {
        var counts = new java.util.TreeMap<String, int[]>();
        for (var pot : pots) {
            if (pot.possession() != PaintPotPossession.OWNED) continue;
            var count = counts.computeIfAbsent(pot.paintProductId(), ignored -> new int[2]);
            count[0]++;
            if (pot.available()) count[1]++;
        }
        return new WorkshopPaintInventory(counts.entrySet().stream()
                .map(entry -> new WorkshopPaintStock(entry.getKey(), entry.getValue()[0], entry.getValue()[1])).toList());
    }

    public Set<String> availablePaintProductIds() {
        return stocks.stream().filter(stock -> stock.availableQuantity() > 0)
                .map(WorkshopPaintStock::paintProductId).collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> ownedPaintProductIds() {
        return stocks.stream().filter(stock -> stock.quantity() > 0)
                .map(WorkshopPaintStock::paintProductId).collect(Collectors.toUnmodifiableSet());
    }

    private static DomainException invalid(String message) {
        return new DomainException("invalid_workshop_paint_inventory", message);
    }
}
