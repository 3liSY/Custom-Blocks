package com.customblocks.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Iterator;

public class ImageProcessor {
    private static final int MAX_DOWNLOAD_BYTES = 32 * 1024 * 1024;
    private static final int TARGET_SIZE = 16; 

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    static {
        // Feature 4: Crucial for WebP/TIFF/ICO support
        ImageIO.scanForPlugins();
    }

    public static byte[] downloadAndProcess(String url, int targetSize) throws Exception {
        targetSize = Math.max(16, Math.min(256, targetSize));
        HttpResponse<byte[]> resp = HTTP.send(
                HttpRequest.newBuilder().uri(URI.create(url))
                        .timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());
        if (resp.body().length > MAX_DOWNLOAD_BYTES) throw new IOException("Image too large");

        return processBytes(resp.body(), targetSize);
    }

    public static byte[] processBytes(byte[] raw, int targetSize) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
        if (src == null) throw new IOException("Unsupported format");
        return resizeToPng(src, targetSize);
    }

    public static byte[] resizeToPng(BufferedImage src, int size) throws IOException {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(out, "PNG", baos);
        return baos.toByteArray();
    }

    /** * Feature 1: PIC EDITOR - Combined logic to prevent quality loss 
     */
    public static byte[] applyUltimateEdits(byte[] png, float brightness, float contrast, int rotation, boolean flipH, boolean flipV, int targetSize) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        int w = src.getWidth(), h = src.getHeight();
        
        // 1. Brightness & Contrast (Manual Pixel Loop for precision)
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = clamp((int)((( (argb >> 16) & 0xFF) - 128) * contrast + 128 + brightness));
                int g = clamp((int)((( (argb >> 8) & 0xFF) - 128) * contrast + 128 + brightness));
                int b = clamp((int)(( (argb & 0xFF) - 128) * contrast + 128 + brightness));
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        // 2. Flip Logic (Feature 1)
        if (flipH || flipV) {
            BufferedImage flipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    out.setRGB(flipH ? w - 1 - x : x, flipV ? h - 1 - y : y, out.getRGB(x, y));
                }
            }
        }

        // 3. Rotation (Feature 1: 90, 180, 270)
        if (rotation != 0) {
            boolean swap = (rotation == 90 || rotation == 270);
            BufferedImage rotated = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = rotated.createGraphics();
            g.translate(rotated.getWidth() / 2.0, rotated.getHeight() / 2.0);
            g.rotate(Math.toRadians(rotation));
            g.drawImage(out, -w / 2, -h / 2, null);
            g.dispose();
            out = rotated;
        }

        return resizeToPng(out, targetSize);
    }

    /** Feature 13: Frame detection for Animated Blocks */
    public static int getFrameCount(byte[] raw) throws IOException {
        try (ImageInputStream is = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(is);
            if (!readers.hasNext()) return 1;
            ImageReader reader = readers.next();
            reader.setInput(is);
            int count = reader.getNumImages(true);
            reader.dispose();
            return count;
        }
    }

    /** Feature 3: Crop Implementation */
    public static byte[] crop(byte[] png, int top, int right, int bottom, int left, int targetSize) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        int w = src.getWidth(), h = src.getHeight();
        int nx = left, ny = top, nw = w - left - right, nh = h - top - bottom;
        if (nw <= 0 || nh <= 0) throw new IOException("Invalid crop");
        return resizeToPng(src.getSubimage(nx, ny, nw, nh), targetSize);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}