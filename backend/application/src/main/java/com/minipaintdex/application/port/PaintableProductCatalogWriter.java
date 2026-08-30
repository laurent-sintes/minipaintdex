package com.minipaintdex.application.port;

import java.util.List;
import java.util.Map;

public interface PaintableProductCatalogWriter {
    void replaceProduct(String productId, Map<String, Object> product, List<Map<String, Object>> paintingGuides);
}
