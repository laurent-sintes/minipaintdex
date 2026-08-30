package com.minipaintdex.application.port;

import java.util.List;
import java.util.Map;

public interface WorkshopPaintInventoryWriter {
    void replaceWorkshopPaints(List<Map<String, Object>> paints);
}
