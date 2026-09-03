package com.minipaintdex.adapter.onnx;

import java.nio.file.Path;

public record PaintPotPhotoPolicy(Path modelPath, String modelSha256, long maxPixels, int maxOutputSize,
        int cpuThreads, double foregroundThreshold, double paddingRatio) {
    public PaintPotPhotoPolicy {
        if (modelPath == null || modelSha256 == null || !modelSha256.matches("[a-f0-9]{64}")
                || maxPixels < 1 || maxOutputSize < 320 || maxOutputSize > 4096 || cpuThreads < 1 || cpuThreads > 8
                || !Double.isFinite(foregroundThreshold) || foregroundThreshold <= 0 || foregroundThreshold >= 1
                || !Double.isFinite(paddingRatio) || paddingRatio < 0 || paddingRatio > .5)
            throw new IllegalArgumentException("Invalid paint-pot photo processing configuration.");
    }
}
