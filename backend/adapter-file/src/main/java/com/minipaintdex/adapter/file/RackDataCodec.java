package com.minipaintdex.adapter.file;

import com.minipaintdex.domain.event.DomainEvent;
import com.minipaintdex.domain.market.storage.RackCatalog;
import com.minipaintdex.domain.workshop.PaintPotEvent.PaintPotContainerIdentified;
import com.minipaintdex.domain.workshop.storage.WorkshopRack;
import com.minipaintdex.domain.workshop.storage.WorkshopPaintStorage;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;

/** Single schema-v1 snake-case mapping for immutable rack records and aggregate-local events. */
final class RackDataCodec {
    private final JsonMapper mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    RackCatalog catalog(Map<String, Object> document) { return mapper.convertValue(document, RackCatalog.class); }
    Map<String, Object> encode(Object value) { return mapper.convertValue(value, MAP); }
    DomainEvent event(String type, Map<String, Object> payload) {
        return switch (type) {
            case "workshop_rack.registered" -> mapper.convertValue(payload, WorkshopRack.Registered.class);
            case "workshop_rack.configured" -> mapper.convertValue(payload, WorkshopRack.Configured.class);
            case "workshop_paint_storage.arrangement_recorded" -> mapper.convertValue(payload, WorkshopPaintStorage.ArrangementRecorded.class);
            case "paint_pot.container_identified" -> mapper.convertValue(payload, PaintPotContainerIdentified.class);
            default -> throw new FileStorageException("Unknown storage event: " + type, null);
        };
    }
}
