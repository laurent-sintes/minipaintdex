package com.minipaintdex.domain.shared.storage;

public record ContainerDimensions(Double widthMm, Double depthMm, Double heightMm) {
    public ContainerDimensions {
        widthMm = StorageFields.dimension(widthMm);
        depthMm = StorageFields.dimension(depthMm);
        heightMm = StorageFields.dimension(heightMm);
    }
    public boolean complete() { return widthMm != null && depthMm != null && heightMm != null; }
    public static ContainerDimensions unknown() { return new ContainerDimensions(null, null, null); }
}
