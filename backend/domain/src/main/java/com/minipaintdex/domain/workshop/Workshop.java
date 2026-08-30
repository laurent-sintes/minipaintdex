package com.minipaintdex.domain.workshop;

import com.minipaintdex.domain.workflow.DomainException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/** Aggregate root representing the owner's workshop and its imported market products. */
public record Workshop(String id, List<WorkshopProduct> products, Instant updatedAt) {
    public static final String DEFAULT_ID = "my-workshop";

    public Workshop {
        if (id == null || id.isBlank()) throw new DomainException("invalid_workshop", "Workshop id is required.");
        products = products == null ? List.of() : List.copyOf(products);
        var ids = new HashSet<String>();
        for (var product : products) {
            if (!ids.add(product.productId())) {
                throw new DomainException("invalid_workshop", "Duplicate workshop product: " + product.productId());
            }
        }
    }

    public boolean containsProduct(String productId) {
        return products.stream().anyMatch(product -> product.productId().equals(productId));
    }
}
