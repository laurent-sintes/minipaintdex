package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.shared.DomainException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable owner-stock value set referencing Market paint identities. */
public record WorkshopPaintInventory(List<Stock> stocks) {
    public WorkshopPaintInventory {
        stocks = stocks == null ? List.of() : List.copyOf(stocks);
        var ids = new HashSet<String>();
        for (var stock : stocks) {
            if (stock == null) throw invalid("Paint inventory cannot contain null entries.");
            if (!ids.add(stock.marketPaintId())) {
                throw invalid("Duplicate workshop paint: " + stock.marketPaintId());
            }
        }
    }

    public Set<String> ownedPaintIds() {
        return stocks.stream().filter(stock -> stock.quantity() > 0)
                .map(Stock::marketPaintId).collect(Collectors.toUnmodifiableSet());
    }

    public record Stock(String marketPaintId, int quantity) {
        public Stock {
            marketPaintId = DomainFields.id(marketPaintId, "marketPaintId");
            if (quantity < 0) throw invalid("Paint quantity cannot be negative: " + marketPaintId);
        }
    }

    private static DomainException invalid(String message) {
        return new DomainException("invalid_workshop_paint_inventory", message);
    }
}
