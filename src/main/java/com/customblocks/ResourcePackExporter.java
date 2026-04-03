package com.customblocks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Generates the Minecraft resource pack ZIP from all stored block data.
 * Supports: default textures, per-face textures, animated textures (.mcmeta),
 * random texture variants (with block state), and custom sounds.
 */
public class ResourcePackExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger("CustomBlocks/Exporter");
    public  static final String MOD_ID = "customblocks";

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public static byte[] buildPackZipBytes(String packVersion) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4 * 1024 * 1024);
        writePackZip(baos, packVersion);
        return baos.toByteArray();
    }

    public static void exportCurrentPackZip(File dir) throws IOException {
        exportCurrentPackZip(dir, "v10");
    }

    public static void exportCurrentPackZip(File dir, String version) throws IOException {
        dir.mkdirs();
        File out = new File(dir, "pack-" + version + ".zip");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            writePackZip(fos, version);
        }
        byte[] bytes = Files.readAllBytes(out.toPath());
        PackHttpServer.updatePack(bytes);
        LOGGER.info("[CustomBlocks] Exported resource pack → {} ({} bytes)", out.getName(), bytes.length);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core ZIP builder
    // ─────────────────────────────────────────────────────────────────────────

    private static void writePackZip(OutputStream target, String version) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(target);
        zip.setLevel(Deflater.BEST_COMPRESSION);

        // pack.mcmeta
        addEntry(zip, "pack.mcmeta", packMeta(version));

        // pack.png (simple 64x64 icon)
        addEntry(zip, "pack.png", generatePackIcon());

        Collection<SlotManager.SlotData> slots = SlotManager.allSlots();

        // Block models and states
        StringBuilder atlasJson = new StringBuilder("[");
        boolean first = true;

        for (SlotManager.SlotData d : slots) {
            String base = "assets/" + MOD_ID;

            // ── Texture(s) ────────────────────────────────────────────────
            if (d.texture != null && d.texture.length > 0) {
                addEntry(zip, base + "/textures/block/slot_" + d.index + ".png", d.texture);

                if (d.isAnimated()) {
                    // Build sprite-sheet from frames (stack vertically)
                    byte[] sheet = buildAnimationSheet(d.animFrames);
                    if (sheet != null)
                        addEntry(zip, base + "/textures/block/slot_" + d.index + "_anim.png", sheet);
                    addEntry(zip, base + "/textures/block/slot_" + d.index + "_anim.png.mcmeta",
                            animMeta(d.animFrames.size(), d.animFps));
                }

                if (d.hasRandom()) {
                    for (int v = 0; v < d.randomVariants.size(); v++)
                        addEntry(zip, base + "/textures/block/slot_" + d.index + "_v" + v + ".png",
                                d.randomVariants.get(v));
                }
            }

            // Per-face textures
            for (Map.Entry<String, byte[]> e : d.faceTextures.entrySet())
                addEntry(zip, base + "/textures/block/slot_" + d.index + "_" + e.getKey() + ".png",
                        e.getValue());

            // ── Block model ───────────────────────────────────────────────
            addEntry(zip, base + "/models/block/slot_" + d.index + ".json",
                    blockModel(d));
            addEntry(zip, base + "/models/item/slot_" + d.index + ".json",
                    itemModel(d));

            // ── Block state ───────────────────────────────────────────────
            addEntry(zip, base + "/blockstates/slot_" + d.index + ".json",
                    blockState(d));

            // ── Sounds ────────────────────────────────────────────────────
            if (d.soundBreak != null)
                addEntry(zip, base + "/sounds/block/slot_" + d.index + "_break.ogg", d.soundBreak);
            if (d.soundPlace != null)
                addEntry(zip, base + "/sounds/block/slot_" + d.index + "_place.ogg", d.soundPlace);
            if (d.soundStep != null)
                addEntry(zip, base + "/sounds/block/slot_" + d.index + "_step.ogg", d.soundStep);

            // Atlas source entry
            if (!first) atlasJson.append(",");
            atlasJson.append("\n  {\"type\":\"single\",\"resource\":\"").append(MOD_ID)
                    .append(":block/slot_").append(d.index).append("\"}");
            first = false;
        }
        atlasJson.append("\n]");

        // Atlas
        addEntry(zip, "assets/" + MOD_ID + "/atlases/blocks.json",
                ("{\"sources\":" + atlasJson + "}").getBytes(StandardCharsets.UTF_8));

        // Sounds.json (custom sounds per block)
        addEntry(zip, "assets/" + MOD_ID + "/sounds.json", buildSoundsJson(slots));

        // Language
        addEntry(zip, "assets/" + MOD_ID + "/lang/en_us.json", buildLangJson(slots));

        zip.finish();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON builders
    // ─────────────────────────────────────────────────────────────────────────

    private static byte[] packMeta(String version) {
        return ("{\"pack\":{\"pack_format\":34,\"description\":\"Custom Blocks Ultimate " + version + " — " +
                SlotManager.usedSlots() + " custom block(s)\"}}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] blockModel(SlotManager.SlotData d) {
        boolean hasFaces = d.hasFaces();
        String ns = MOD_ID + ":block/slot_" + d.index;
        String top   = hasFaces && d.faceTextures.containsKey("top")    ? ns + "_top"    : ns;
        String bot   = hasFaces && d.faceTextures.containsKey("bottom") ? ns + "_bottom" : ns;
        String north = hasFaces && d.faceTextures.containsKey("north")  ? ns + "_north"  : ns;
        String south = hasFaces && d.faceTextures.containsKey("south")  ? ns + "_south"  : ns;
        String east  = hasFaces && d.faceTextures.containsKey("east")   ? ns + "_east"   : ns;
        String west  = hasFaces && d.faceTextures.containsKey("west")   ? ns + "_west"   : ns;

        String tex = d.isAnimated() ? ns + "_anim" : ns;

        return ("{\"parent\":\"minecraft:block/cube\",\"textures\":{" +
                "\"up\":\"" + (hasFaces && d.faceTextures.containsKey("top") ? top : tex) + "\"," +
                "\"down\":\"" + (hasFaces && d.faceTextures.containsKey("bottom") ? bot : tex) + "\"," +
                "\"north\":\"" + (hasFaces && d.faceTextures.containsKey("north") ? north : tex) + "\"," +
                "\"south\":\"" + (hasFaces && d.faceTextures.containsKey("south") ? south : tex) + "\"," +
                "\"east\":\"" + (hasFaces && d.faceTextures.containsKey("east") ? east : tex) + "\"," +
                "\"west\":\"" + (hasFaces && d.faceTextures.containsKey("west") ? west : tex) + "\"," +
                "\"particle\":\"" + tex + "\"}}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] itemModel(SlotManager.SlotData d) {
        return ("{\"parent\":\"" + MOD_ID + ":block/slot_" + d.index + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] blockState(SlotManager.SlotData d) {
        if (!d.hasRandom()) {
            return ("{\"variants\":{\"\":{\"model\":\"" + MOD_ID + ":block/slot_" + d.index + "\"}}}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        // Random variants: use multipart with weight
        StringBuilder sb = new StringBuilder("{\"variants\":{\"\":[");
        sb.append("{\"model\":\"").append(MOD_ID).append(":block/slot_").append(d.index).append("\",\"weight\":10}");
        for (int v = 0; v < d.randomVariants.size(); v++) {
            sb.append(",{\"model\":\"").append(MOD_ID).append(":block/slot_").append(d.index)
              .append("_v").append(v).append("\",\"weight\":10}");
        }
        sb.append("]}}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] animMeta(int frames, int fps) {
        int frameTime = Math.max(1, 20 / Math.max(1, fps)); // ticks per frame
        StringBuilder sb = new StringBuilder("{\"animation\":{\"frametime\":" + frameTime + ",\"frames\":[");
        for (int i = 0; i < frames; i++) {
            if (i > 0) sb.append(",");
            sb.append(i);
        }
        sb.append("]}}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] buildSoundsJson(Collection<SlotManager.SlotData> slots) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (SlotManager.SlotData d : slots) {
            boolean hasCustom = d.soundBreak != null || d.soundPlace != null || d.soundStep != null;
            if (!hasCustom) continue;
            if (!first) sb.append(",");
            first = false;
            String base = "customblocks:block/slot_" + d.index;
            if (d.soundBreak != null) {
                sb.append("\"customblocks.block.slot_").append(d.index).append(".break\":{\"sounds\":[{\"name\":\"").append(base).append("_break\"}]}");
            }
        }
        sb.append("}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] buildLangJson(Collection<SlotManager.SlotData> slots) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (SlotManager.SlotData d : slots) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"block.customblocks.slot_").append(d.index).append("\":\"")
              .append(d.displayName.replace("\"", "\\\"")).append("\"");
        }
        sb.append("}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static void addEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        if (data == null || data.length == 0) return;
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    /** Stack animation frames vertically into a single sprite sheet. */
    private static byte[] buildAnimationSheet(List<byte[]> frames) {
        try {
            if (frames.isEmpty()) return null;
            java.awt.image.BufferedImage first = javax.imageio.ImageIO.read(
                    new java.io.ByteArrayInputStream(frames.get(0)));
            int w = first.getWidth(), h = first.getHeight();
            java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(w, h * frames.size(),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = sheet.createGraphics();
            for (int i = 0; i < frames.size(); i++) {
                java.awt.image.BufferedImage frame = javax.imageio.ImageIO.read(
                        new java.io.ByteArrayInputStream(frames.get(i)));
                g.drawImage(frame, 0, i * h, null);
            }
            g.dispose();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(sheet, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) { return null; }
    }

    /** Generate a simple 64x64 pack icon PNG. */
    private static byte[] generatePackIcon() {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(64, 64,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            // Gradient background
            java.awt.GradientPaint gp = new java.awt.GradientPaint(0, 0, new java.awt.Color(0x1A1A3A),
                    64, 64, new java.awt.Color(0x3A1A5A));
            g.setPaint(gp);
            g.fillRect(0, 0, 64, 64);
            // Draw "CB" text
            g.setColor(new java.awt.Color(0x44FF44));
            g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 24));
            g.drawString("CB", 10, 42);
            g.setColor(new java.awt.Color(0x5555EE));
            g.drawRect(2, 2, 59, 59);
            g.dispose();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) { return new byte[0]; }
    }
}
