package com.minipaintdex.application.port;

import java.util.List;
import java.util.Map;

public interface MarketPaintCatalogWriter {
    void replaceMarketPaints(List<Map<String, Object>> paints);

    default void replaceMarketPaintsAndWorkshopInventory(
            List<Map<String, Object>> paints,
            List<Map<String, Object>> inventory,
            WorkshopPaintInventoryWriter inventoryWriter) {
        replaceMarketPaints(paints);
        inventoryWriter.replaceWorkshopPaints(inventory);
    }
}
