package com.minipaintdex.adapter.onnx;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.minipaintdex.domain.shared.DomainException;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

final class PaintPotRaster {
    private PaintPotRaster() {}

    static BufferedImage decode(byte[] bytes, PaintPotPhotoPolicy policy) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("Photo is not a readable JPEG, PNG or WebP image.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!Set.of("jpeg", "png", "webp").contains(reader.getFormatName().toLowerCase(Locale.ROOT)))
                    throw invalid("Unsupported photo format.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 2 || height < 2 || (long) width * height > policy.maxPixels())
                    throw invalid("Photo dimensions exceed the processing limit.");
                var params = reader.getDefaultReadParam();
                int subsampling = Math.max(1, Math.max(width, height) / policy.maxOutputSize());
                params.setSourceSubsampling(subsampling, subsampling, 0, 0);
                var image = orient(reader.read(0, params), orientation(bytes));
                double scale = Math.min(1d, policy.maxOutputSize() / (double) Math.max(image.getWidth(), image.getHeight()));
                return resize(image, Math.max(1, (int) Math.round(image.getWidth() * scale)), Math.max(1, (int) Math.round(image.getHeight() * scale)));
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException failure) {
            throw invalid("Photo cannot be decoded.");
        }
    }

    private static int orientation(byte[] bytes) {
        try {
            var exif = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes)).getFirstDirectoryOfType(ExifIFD0Directory.class);
            return exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION) ? exif.getInt(ExifIFD0Directory.TAG_ORIENTATION) : 1;
        } catch (com.drew.imaging.ImageProcessingException | com.drew.metadata.MetadataException | IOException ignored) { return 1; }
    }

    static BufferedImage orient(BufferedImage image, int orientation) {
        if (orientation < 2 || orientation > 8) return image;
        int w = image.getWidth(), h = image.getHeight();
        var out = new BufferedImage(orientation >= 5 ? h : w, orientation >= 5 ? w : h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int dx = switch (orientation) { case 2, 3 -> w - 1 - x; case 5, 8 -> y; case 6, 7 -> h - 1 - y; default -> x; };
            int dy = switch (orientation) { case 3, 4 -> h - 1 - y; case 5, 6 -> x; case 7, 8 -> w - 1 - x; default -> y; };
            out.setRGB(dx, dy, image.getRGB(x, y));
        }
        return out;
    }

    static BufferedImage resize(BufferedImage image, int width, int height) {
        var out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = out.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        return out;
    }

    static byte[] cutout(BufferedImage image, float[][] scores, PaintPotPhotoPolicy policy) throws IOException {
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (var row : scores) for (var score : row) {
            if (!Float.isFinite(score)) throw invalid("The image did not produce a usable foreground mask.");
            min = Math.min(min, score); max = Math.max(max, score);
        }
        if (max - min < .000001f) throw invalid("No distinct foreground could be found; keep the original photo.");
        var smallMask = new BufferedImage(scores[0].length, scores.length, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < scores.length; y++) for (int x = 0; x < scores[y].length; x++) {
            int grey = Math.round(255 * (scores[y][x] - min) / (max - min));
            smallMask.setRGB(x, y, grey * 0x010101);
        }
        var mask = resize(smallMask, image.getWidth(), image.getHeight());
        int x0 = image.getWidth(), y0 = image.getHeight(), x1 = -1, y1 = -1;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int rgb = image.getRGB(x, y);
            int alpha = (mask.getRGB(x, y) & 255) * (rgb >>> 24) / 255;
            image.setRGB(x, y, (alpha << 24) | (rgb & 0xffffff));
            if (alpha > policy.foregroundThreshold() * 255) {
                x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
            }
        }
        if (x1 < x0 || y1 < y0) throw invalid("No visible foreground was found; keep the original photo.");
        int width = x1 - x0 + 1, height = y1 - y0 + 1;
        int size = Math.min(policy.maxOutputSize(), (int) Math.ceil(Math.max(width, height) * (1 + 2 * policy.paddingRatio())));
        double scale = Math.min(1, size / (Math.max(width, height) * (1 + 2 * policy.paddingRatio())));
        var cropped = resize(image.getSubimage(x0, y0, width, height), Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale)));
        var canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var graphics = canvas.createGraphics();
        try { graphics.drawImage(cropped, (size - cropped.getWidth()) / 2, (size - cropped.getHeight()) / 2, null); }
        finally { graphics.dispose(); }
        var out = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", out);
        return out.toByteArray();
    }

    private static DomainException invalid(String message) { return new DomainException("invalid_input", message); }
}
