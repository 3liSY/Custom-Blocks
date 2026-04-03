package com.customblocks;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * Template manager: 15 built-in presets + user-defined templates in
 * config/customblocks/templates.json.
 *
 * Templates define metadata; textures are fetched from URLs at apply-time.
 */
public class TemplateManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("CustomBlocks/Templates");
    private static final Gson   GSON   = new GsonBuilder().setPrettyPrinting().create();
    private static final String PATH   = "config/customblocks/templates.json";

    public record Template(String id, String displayName, int lightLevel,
                           float hardness, String soundType, String textureUrl, String description) {}

    // 15 built-in presets
    private static final List<Template> BUILT_IN = List.of(
        new Template("neon_red",     "Neon Red",        15, 1.5f, "glass",  "", "Glowing red block"),
        new Template("neon_blue",    "Neon Blue",       15, 1.5f, "glass",  "", "Glowing blue block"),
        new Template("neon_green",   "Neon Green",      15, 1.5f, "glass",  "", "Glowing green block"),
        new Template("neon_yellow",  "Neon Yellow",     12, 1.5f, "glass",  "", "Glowing yellow block"),
        new Template("neon_pink",    "Neon Pink",       13, 1.5f, "glass",  "", "Glowing pink block"),
        new Template("wood_oak",     "Oak Plank",        0, 3.0f, "wood",   "", "Classic oak wood"),
        new Template("stone_smooth", "Smooth Stone",     0, 5.0f, "stone",  "", "Smooth stone slab"),
        new Template("metal_iron",   "Iron Panel",       0, 5.0f, "metal",  "", "Industrial iron"),
        new Template("glass_clear",  "Clear Glass",      0, 0.3f, "glass",  "", "Transparent glass"),
        new Template("sand_yellow",  "Desert Sand",      0, 0.3f, "sand",   "", "Sandy desert block"),
        new Template("wool_white",   "White Wool",       0, 0.3f, "wool",   "", "Soft white wool"),
        new Template("grass_green",  "Grass Top",        0, 1.5f, "grass",  "", "Grass surface"),
        new Template("portal_swirl", "Portal Swirl",    11, 0.3f, "glass",  "", "Animated portal"),
        new Template("lava_glow",    "Lava Glow",       15, 5.0f, "stone",  "", "Hot lava texture"),
        new Template("diamond_ore",  "Diamond Ore",      0, 5.0f, "stone",  "", "Sparkling diamond ore")
    );

    private static final Map<String, Template> CUSTOM = new LinkedHashMap<>();

    public static void load() {
        CUSTOM.clear();
        Path p = Paths.get(PATH);
        if (!Files.exists(p)) { createDefault(); return; }
        try {
            JsonArray arr = JsonParser.parseString(Files.readString(p)).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String tid   = o.get("id").getAsString();
                String name  = o.get("displayName").getAsString();
                int    light = o.has("lightLevel") ? o.get("lightLevel").getAsInt() : 0;
                float  hard  = o.has("hardness")   ? o.get("hardness").getAsFloat() : 1.5f;
                String snd   = o.has("soundType")  ? o.get("soundType").getAsString() : "stone";
                String url   = o.has("textureUrl") ? o.get("textureUrl").getAsString() : "";
                String desc  = o.has("description")? o.get("description").getAsString() : "";
                CUSTOM.put(tid, new Template(tid, name, light, hard, snd, url, desc));
            }
            LOGGER.info("[CustomBlocks] Loaded {} custom templates.", CUSTOM.size());
        } catch (Exception e) {
            LOGGER.error("[CustomBlocks] Template load error: {}", e.getMessage());
        }
    }

    private static void createDefault() {
        try {
            Files.createDirectories(Paths.get("config/customblocks"));
            JsonArray arr = new JsonArray();
            JsonObject example = new JsonObject();
            example.addProperty("id", "my_block");
            example.addProperty("displayName", "My Custom Block");
            example.addProperty("lightLevel", 0);
            example.addProperty("hardness", 1.5);
            example.addProperty("soundType", "stone");
            example.addProperty("textureUrl", "https://example.com/texture.png");
            example.addProperty("description", "Example custom template");
            arr.add(example);
            Files.writeString(Paths.get(PATH), GSON.toJson(arr));
        } catch (IOException ignored) {}
    }

    public static List<Template> allTemplates() {
        List<Template> all = new ArrayList<>(BUILT_IN);
        all.addAll(CUSTOM.values());
        return Collections.unmodifiableList(all);
    }

    public static Optional<Template> get(String id) {
        return BUILT_IN.stream().filter(t -> t.id().equals(id))
                .findFirst()
                .or(() -> Optional.ofNullable(CUSTOM.get(id)));
    }

    /**
     * Apply a template: creates a block with the template's properties.
     * If the template has a textureUrl, fetches the texture.
     */
    public static SlotManager.SlotData apply(String templateId, String blockId, String blockName) {
        Optional<Template> opt = get(templateId);
        if (opt.isEmpty()) return null;
        Template t = opt.get();

        byte[] tex = null;
        if (t.textureUrl() != null && !t.textureUrl().isEmpty()) {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10)).build();
                HttpResponse<byte[]> resp = client.send(
                        HttpRequest.newBuilder().uri(URI.create(t.textureUrl()))
                                .timeout(Duration.ofSeconds(15)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() == 200) tex = resp.body();
            } catch (Exception e) {
                LOGGER.warn("[CustomBlocks] Template texture fetch failed: {}", e.getMessage());
            }
        }
        // Generate colored placeholder if no URL
        if (tex == null) tex = generateColoredTexture(templateId);

        SlotManager.SlotData d = SlotManager.assign(blockId, blockName, tex);
        if (d == null) return null;
        d.lightLevel = t.lightLevel();
        d.hardness   = t.hardness();
        d.soundType  = t.soundType();
        return d;
    }

    /** Generate a simple 16x16 solid-color PNG for built-in templates without a URL. */
    private static byte[] generateColoredTexture(String templateId) {
        try {
            int color = switch (templateId) {
                case "neon_red"    -> 0xFF4400;
                case "neon_blue"   -> 0x0044FF;
                case "neon_green"  -> 0x00FF44;
                case "neon_yellow" -> 0xFFFF00;
                case "neon_pink"   -> 0xFF44AA;
                case "wood_oak"    -> 0x8B5A2B;
                case "stone_smooth"-> 0x909090;
                case "metal_iron"  -> 0xC0C0C0;
                case "glass_clear" -> 0xAADDFF;
                case "sand_yellow" -> 0xF4D476;
                case "wool_white"  -> 0xF0F0F0;
                case "grass_green" -> 0x4CAF50;
                case "portal_swirl"-> 0x5500AA;
                case "lava_glow"   -> 0xFF6600;
                case "diamond_ore" -> 0x00FFEE;
                default            -> 0x808080;
            };
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(16, 16,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setColor(new java.awt.Color(color));
            g.fillRect(0, 0, 16, 16);
            // Draw a simple cross pattern
            g.setColor(new java.awt.Color((color >> 1) & 0x7F7F7F));
            g.fillRect(7, 0, 2, 16);
            g.fillRect(0, 7, 16, 2);
            g.dispose();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) { return null; }
    }
}
