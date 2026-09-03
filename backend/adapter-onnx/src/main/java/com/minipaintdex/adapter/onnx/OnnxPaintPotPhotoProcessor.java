package com.minipaintdex.adapter.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.minipaintdex.application.port.PaintPotPhotoProcessor;
import com.minipaintdex.application.result.PaintPotPhotoPreview;
import com.minipaintdex.domain.shared.DomainException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/** Owns one lazily opened CPU session. No model download or network client exists at runtime. */
public final class OnnxPaintPotPhotoProcessor implements PaintPotPhotoProcessor, AutoCloseable {
    private static final int INPUT_SIZE = 320;
    private final PaintPotPhotoPolicy policy;
    private final ReentrantLock inference = new ReentrantLock();
    private OrtSession session;
    private boolean closed;

    public OnnxPaintPotPhotoProcessor(PaintPotPhotoPolicy policy) {
        this.policy = policy;
        try (var stream = Files.newInputStream(policy.modelPath())) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[8192];
            for (int length; (length = stream.read(buffer)) >= 0;) digest.update(buffer, 0, length);
            if (!HexFormat.of().formatHex(digest.digest()).equals(policy.modelSha256()))
                throw new IllegalStateException("Paint-pot photo model checksum mismatch.");
        } catch (IOException | java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Install the local photo model with scripts/install-photo-model.ps1 or disable minipaintdex.paint-pot-photos.enabled.", failure);
        }
    }

    @Override public PaintPotPhotoPreview removeBackground(byte[] content, String correlationId) {
        if (!inference.tryLock()) throw unavailable("Photo processing is busy; retry when the current preview finishes.");
        try {
            if (closed) throw unavailable("Photo processing is stopping.");
            var raster = PaintPotRaster.decode(content, policy);
            var model = session();
            try (var input = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(),
                    FloatBuffer.wrap(normalize(raster)), new long[] {1, 3, INPUT_SIZE, INPUT_SIZE});
                 var result = model.run(Map.of(model.getInputNames().iterator().next(), input))) {
                var mask = ((float[][][][]) result.get(0).getValue())[0][0];
                return new PaintPotPhotoPreview(PaintPotRaster.cutout(raster, mask, policy),
                        "u2netp-" + policy.modelSha256().substring(0, 12) + "-v1", correlationId);
            }
        } catch (OrtException | IOException failure) {
            throw unavailable("Local photo processing failed: " + failure.getClass().getSimpleName());
        } finally {
            inference.unlock();
        }
    }

    private OrtSession session() throws OrtException {
        if (session == null) {
            try (var options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(policy.cpuThreads());
                options.setInterOpNumThreads(1);
                options.setCPUArenaAllocator(false);
                options.setMemoryPatternOptimization(false);
                session = OrtEnvironment.getEnvironment().createSession(policy.modelPath().toString(), options);
            }
        }
        return session;
    }

    static float[] normalize(BufferedImage source) {
        var image = PaintPotRaster.resize(source, INPUT_SIZE, INPUT_SIZE);
        var plane = INPUT_SIZE * INPUT_SIZE;
        var tensor = new float[3 * plane];
        var pixels = image.getRGB(0, 0, INPUT_SIZE, INPUT_SIZE, null, 0, INPUT_SIZE);
        int max = 1;
        for (var pixel : pixels) for (var channel = 0; channel < 3; channel++) max = Math.max(max, (pixel >> (16 - 8 * channel)) & 255);
        float[] mean = {.485f, .456f, .406f};
        float[] std = {.229f, .224f, .225f};
        for (var index = 0; index < plane; index++) for (var channel = 0; channel < 3; channel++)
            tensor[channel * plane + index] = (((pixels[index] >> (16 - 8 * channel)) & 255) / (float) max - mean[channel]) / std[channel];
        return tensor;
    }

    private static DomainException unavailable(String message) { return new DomainException("photo_processing_unavailable", message); }

    @Override public void close() throws OrtException {
        // Shutdown cannot free the native session while a caller is still using its tensors.
        inference.lock();
        try { closed = true; if (session != null) { session.close(); session = null; } }
        finally { inference.unlock(); }
    }
}
