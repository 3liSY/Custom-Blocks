package com.customblocks.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/**
 * Utility for downloading, converting, and resizing images on the server.
 * Supports PNG, JPEG, GIF, BMP, WebP, TIFF, ICO via TwelveMonkeys.
 */
public class ImageProcessor {

    private static final int MAX_DOWNLOAD_BYTES = 32 * 1024 * 1024;
    private static final int TARGET_SIZE        = 16; // default Minecraft texture size

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Download image from URL, resize to targetSize × targetSize, return PNG bytes.
     * @param url       Source URL
     * @param targetSize Output dimension (16-256, clamped)
     */
    public static byte[] downloadAndProcess(String url, int targetSize) throws Exception {
        targetSize = Math.max(16, Math.min(256, targetSize));

        HttpResponse<byte[]> resp = HTTP.send(
                HttpRequest.newBuilder().uri(URI.create(url))
                        .timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        if (resp.statusCode() != 200)
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);

        byte[] raw = resp.body();
        if (raw.length > MAX_DOWNLOAD_BYTES)
            throw new IOException("Image too large: " + raw.length + " bytes");

        return processBytes(raw, targetSize);
    }

    /**
     * Process raw image bytes → resize to targetSize PNG.
     */
    public static byte[] processBytes(byte[] raw, int targetSize) throws Exception {
        targetSize = Math.max(16, Math.min(256, targetSize));
        // TwelveMonkeys registers its readers when ImageIO is first called
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
        if (src == null) throw new IOException("Unsupported image format");
        return resizeToPng(src, targetSize);
    }

    public static byte[] processBytes(byte[] raw) throws Exception {
        return processBytes(raw, TARGET_SIZE);
    }

    /**
     * Resize src → targetSize × targetSize, output as PNG bytes.
     */
    public static byte[] resizeToPng(BufferedImage src, int size) throws IOException {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(out, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * Apply brightness/contrast adjustments.
     * brightness: -100 to +100, contrast: 0.1 to 3.0
     */
    public static byte[] adjustBrightnessContrast(byte[] png, float brightness, float contrast) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        if (src == null) throw new IOException("Invalid PNG");
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >>  8) & 0xFF;
                int b =  argb        & 0xFF;
                r = clamp((int)((r - 128) * contrast + 128 + brightness));
                g = clamp((int)((g - 128) * contrast + 128 + brightness));
                b = clamp((int)((b - 128) * contrast + 128 + brightness));
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(out, "PNG", baos);
        return baos.toByteArray();
    }

    /** Flip horizontally */
    public static byte[] flipHorizontal(byte[] png) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(w - 1 - x, y, src.getRGB(x, y));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(out, "PNG", baos);
        return baos.toByteArray();
    }

    /** Flip vertically */
    public static byte[] flipVertical(byte[] png) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(x, h - 1 - y, src.getRGB(x, y));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(out, "PNG", baos);
        return baos.toByteArray();
    }

    /** Rotate by degrees (90, 180, 270) */
    public static byte[] rotate(byte[] png, int degrees) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        int w = src.getWidth(), h = src.getHeight();
        boolean swap = (degrees == 90 || degrees == 270);
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.translate(out.getWidth() / 2.0, out.getHeight() / 2.0);
        g.rotate(Math.toRadians(degrees));
        g.drawImage(src, -w / 2, -h / 2, null);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(out, "PNG", baos);
        return baos.toByteArray();
    }

    /** Crop: pixels from each edge */
    public static byte[] crop(byte[] png, int top, int right, int bottom, int left, int targetSize) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        int w = src.getWidth(), h = src.getHeight();
        int nx = left, ny = top, nw = w - left - right, nh = h - top - bottom;
        if (nw <= 0 || nh <= 0) throw new IOException("Crop result is empty");
        BufferedImage cropped = src.getSubimage(nx, ny, nw, nh);
        return resizeToPng(cropped, targetSize);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
