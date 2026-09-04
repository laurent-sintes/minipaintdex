package com.minipaintdex.domain.shared.storage;

import java.util.List;

public record RackRowDefinition(String id, String name, String support, Double widthMm,
        Double depthMm, Double clearanceHeightMm, boolean openTop, String evidenceStatus, List<Slot> slots,
        List<CapacityCalibration> capacityCalibrations) {
    public RackRowDefinition(String id, String name, String support, Double widthMm, Double depthMm,
            Double clearanceHeightMm, boolean openTop, String evidenceStatus, List<Slot> slots) {
        this(id, name, support, widthMm, depthMm, clearanceHeightMm, openTop, evidenceStatus, slots, List.of());
    }
    public RackRowDefinition {
        id = StorageFields.id(id); name = StorageFields.text(name, "name");
        if (!List.of("continuous", "fixed-slots").contains(support)) throw StorageFields.invalid("Invalid rack support.");
        widthMm = StorageFields.dimension(widthMm); depthMm = StorageFields.dimension(depthMm);
        clearanceHeightMm = StorageFields.dimension(clearanceHeightMm);
        if (openTop && clearanceHeightMm != null) throw StorageFields.invalid("Open top cannot also have a height limit.");
        evidenceStatus = StorageFields.evidence(evidenceStatus);
        slots = slots == null ? List.of() : List.copyOf(slots);
        if ("continuous".equals(support) && !slots.isEmpty()) throw StorageFields.invalid("Continuous rows do not have fixed slots.");
        if ("fixed-slots".equals(support) && slots.isEmpty()) throw StorageFields.invalid("Fixed-slot rows require slots.");
        if (slots.stream().map(Slot::id).distinct().count() != slots.size()) throw StorageFields.invalid("Duplicate slot ID.");
        capacityCalibrations = capacityCalibrations == null ? List.of() : List.copyOf(capacityCalibrations);
        if (!capacityCalibrations.isEmpty() && !"continuous".equals(support)) throw StorageFields.invalid("Only continuous rows support capacity calibration.");
        var calibratedIds = capacityCalibrations.stream().flatMap(value -> value.containerFormatIds().stream()).toList();
        if (calibratedIds.stream().distinct().count() != calibratedIds.size()) throw StorageFields.invalid("Ambiguous capacity calibration.");
    }
    public record CapacityCalibration(List<String> containerFormatIds, int potCount, boolean heightVerified, String note) {
        public CapacityCalibration {
            containerFormatIds = List.copyOf(containerFormatIds);
            if (containerFormatIds.isEmpty() || potCount < 1) throw StorageFields.invalid("Calibration requires formats and a positive capacity.");
            containerFormatIds.forEach(StorageFields::id);
            note = StorageFields.text(note, "calibration note");
        }
    }
    public Double occupiedFraction(String containerFormatId) {
        if (containerFormatId == null) return null;
        return capacityCalibrations.stream().filter(value -> value.containerFormatIds().contains(containerFormatId))
                .map(value -> 1.0 / value.potCount()).findFirst().orElse(null);
    }
    public static List<RackRowDefinition> validateRows(List<RackRowDefinition> values) {
        if (values == null) throw StorageFields.invalid("A rack requires rows.");
        var rows = List.copyOf(values);
        if (rows.isEmpty() || rows.stream().map(RackRowDefinition::id).distinct().count() != rows.size())
            throw StorageFields.invalid("Rack requires unique, nonempty rows.");
        return rows;
    }
    public record Slot(String id, Double widthMm, Double depthMm, List<String> acceptedFormatIds) {
        public Slot {
            id = StorageFields.id(id); widthMm = StorageFields.dimension(widthMm); depthMm = StorageFields.dimension(depthMm);
            acceptedFormatIds = acceptedFormatIds == null ? List.of() : List.copyOf(acceptedFormatIds);
            acceptedFormatIds.forEach(StorageFields::id);
        }
    }
}
