package com.minipaintdex.application.port;

import com.minipaintdex.domain.event.DomainEvent;

import java.util.List;
import java.util.Map;

public record DataSnapshot(
        Map<String, Object> site,
        List<Map<String, Object>> marketPaints,
        List<Map<String, Object>> paintInventory,
        List<Map<String, Object>> games,
        List<Map<String, Object>> marketPaintingGuides,
        List<Map<String, Object>> shopping,
        List<DomainEvent> events) {
}
