package com.minipaintdex.adapter.onnx;

import com.minipaintdex.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class PaintPotPhotoProcessorTest {
    private static final String HASH = "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8";
    private static PaintPotPhotoPolicy policy(long maxPixels) {
        return new PaintPotPhotoPolicy(Path.of("../../.tools/models/u2netp.onnx"), HASH, maxPixels, 1200, 2, .1, .08);
    }

    @Test void rejectsInvalidImagesAndOversizedDimensionsBeforeInference() throws Exception {
        assertThrows(DomainException.class, () -> PaintPotRaster.decode(new byte[]{1, 2, 3}, policy(24000000)));
        var bytes = png(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB));
        assertThrows(DomainException.class, () -> PaintPotRaster.decode(bytes, policy(1000)));
        assertThrows(IllegalArgumentException.class, () -> new PaintPotPhotoPolicy(Path.of("model"), HASH, 0, 1200, 2, .1, .08));
    }

    @Test void preservesColorAndExistingTransparencyOnTheCroppedSquare() throws Exception {
        var image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        var scores = new float[100][100];
        for (int y = 20; y < 80; y++) for (int x = 30; x < 70; x++) { image.setRGB(x, y, 0x80ff0000); scores[y][x] = 1; }
        var output = ImageIO.read(new ByteArrayInputStream(PaintPotRaster.cutout(image, scores, policy(24000000))));
        assertEquals(output.getWidth(), output.getHeight());
        assertTrue(output.getWidth() < 100);
        assertEquals(0, output.getRGB(0, 0) >>> 24);
        assertEquals(0x80ff0000, output.getRGB(output.getWidth() / 2, output.getHeight() / 2));
        assertThrows(DomainException.class, () -> PaintPotRaster.cutout(image, new float[2][2], policy(24000000)));
        assertThrows(DomainException.class, () -> PaintPotRaster.cutout(image, new float[][]{{Float.NaN, 1}, {0, 1}}, policy(24000000)));
    }

    @Test void rotatesAndMirrorsExifWithoutDroppingPixels() {
        var image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffff0000); image.setRGB(1, 2, 0xff0000ff);
        for (int orientation = 2; orientation <= 8; orientation++) {
            var output = PaintPotRaster.orient(image, orientation);
            assertEquals(orientation >= 5 ? 3 : 2, output.getWidth());
            int red = 0, blue = 0;
            for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) {
                if (output.getRGB(x, y) == 0xffff0000) red++;
                if (output.getRGB(x, y) == 0xff0000ff) blue++;
            }
            assertEquals(1, red); assertEquals(1, blue);
        }
        var rotated = PaintPotRaster.orient(image, 6);
        assertEquals(0xffff0000, rotated.getRGB(2, 0));
        assertEquals(0xff0000ff, rotated.getRGB(0, 1));
    }

    @Test void runsTheInstalledPinnedModelOnCpuAndClosesIt() throws Exception {
        var policy = policy(24000000);
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(policy.modelPath()), "Install scripts/install-photo-model.ps1 for the real-model smoke test.");
        var image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0, 0, 400, 400);
            g.setColor(new Color(0x2855a0)); g.fillRoundRect(130, 110, 140, 230, 30, 30);
            g.setColor(Color.DARK_GRAY); g.fillRect(140, 70, 120, 55);
            g.setColor(Color.LIGHT_GRAY); g.fillRect(140, 170, 120, 100);
            g.setColor(Color.BLACK); g.drawString("PAINT", 165, 225);
        } finally { g.dispose(); }
        var content = png(image);
        var processor = new OnnxPaintPotPhotoProcessor(policy);
        try {
            var preview = processor.removeBackground(content, "photo-test");
            assertEquals("photo-test", preview.correlationId());
            assertTrue(preview.processingMethod().startsWith("u2netp-309c8469258d"));
            var output = ImageIO.read(new ByteArrayInputStream(preview.content()));
            assertEquals(output.getWidth(), output.getHeight());
            assertEquals(0, output.getRGB(0, 0) >>> 24);
            assertTrue((output.getRGB(output.getWidth() / 2, output.getHeight() / 2) >>> 24) > 200);
            assertArrayEquals(preview.content(), processor.removeBackground(content, "repeat").content());
        } finally { processor.close(); }
        assertEquals("photo_processing_unavailable", assertThrows(DomainException.class,
                () -> processor.removeBackground(content, "closed")).code());
        assertThrows(IllegalStateException.class, () -> new OnnxPaintPotPhotoProcessor(new PaintPotPhotoPolicy(
                policy.modelPath(), "0".repeat(64), 24000000, 1200, 2, .1, .08)));
    }

    private static byte[] png(BufferedImage image) throws Exception {
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes); return bytes.toByteArray();
    }
}
