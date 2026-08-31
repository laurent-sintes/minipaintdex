package com.minipaintdex.domain.market.product;

import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaintableProductTest {
    @Test
    void validatesTheExpectedPhysicalQuantity() {
        var error = assertThrows(DomainException.class, () -> new PaintableProduct(
                1, "starter", "Starter", "Line", "boxed_set", "core box", 2,
                new PaintableProduct.Edition("", ""), List.of(),
                List.of(new PaintableProduct.CatalogItem(
                        "starter-hero", "starter", "Hero", "hero", 1, "", false, List.of(), List.of()))));
        assertEquals("invalid_product", error.code());
    }
}
