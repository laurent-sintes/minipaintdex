package com.minipaintdex.application.port;

import java.util.List;
import java.util.Map;

public interface MarketPaintCatalogWriter {
    void replaceMarketPaints(List<Map<String, Object>> paints);
}
