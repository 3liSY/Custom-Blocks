package com.itemmap.manager;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side store of all FrameData, plus custom image bytes.
 * Persisted to config/itemmap/frames.json + images/*.png
 */
public class FrameManager {

    public static final Logger LOGGER = LoggerFactory.getLogger("ItemMap/FrameManager");
    private static final Gson  GSON   = new GsonBuilder().setPrettyPrinting().create();

    // entityId -> FrameData
    private static final Map<Long, FrameData> FRAMES = new ConcurrentHashMap<>();
    // imageId -> raw PNG bytes
    private static final Map<String, byte[]>  IMAGES = new ConcurrentHashMap<>();

    // ── Queries ──────────────────────────────────────────────────────────────

    public static FrameData get(long entityId)          { return FRAMES.get(entityId); }
    public static Collection<FrameData> all()           { return Collections.unmodifiableCollection(FRAMES.values()); }
    public static Set<Long>             allIds()        { return Collections.unmodifiableSet(FRAMES.keySet()); }
    public static byte[]                getImage(String id) { return IMAGES.get(id); }
    public static Set<String>           allImageIds()   { return Collections.unmodifiableSet(IMAGES.keySet()); }

    /** Get or create a FrameData for this entity. */
    public static FrameData getOrCreate(long entityId) {
        return FRAMES.computeIfAbsent(entityId, FrameData::new);
    }

    public static boolean has(long entityId) { return FRAMES.containsKey(entityId); }

    // ── Mutation ─────────────────────────────────────────────────────────────

    public static void put(FrameData data)              { FRAMES.put(data.entityId, data); }

    public static boolean remove(long entityId) {
        return FRAMES.remove(entityId) != null;
    }

    public static void putImage(String id, byte[] png)  { IMAGES.put(id, png); }
    public static boolean removeImage(String id)        { return IMAGES.remove(id) != null; }

    public static void clearAll() {
        FRAMES.clear();
        IMAGES.clear();
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private static File getConfigDir() { return new File("config/itemmap"); }

    public static void saveAll() {
        File dir = getConfigDir();
        dir.mkdirs();
        new File(dir, "images").mkdirs();

        JsonArray arr = new JsonArray();
        for (FrameData d : FRAMES.values()) {
            JsonObject e = new JsonObject();
            e.addProperty("entityId",      d.entityId);
            e.addProperty("mode",          d.mode.name());
            e.addProperty("spinSpeed",     d.spinSpeed);
            e.addProperty("scale",         d.scale);
            e.addProperty("padPct",        d.padPct);
            e.addProperty("glowing",       d.glowing);
            e.addProperty("label",         d.label != null ? d.label : "");
            e.addProperty("bgColor",       d.bgColor);
            e.addProperty("customImageId", d.customImageId != null ? d.customImageId : "");
            e.addProperty("invisible",     d.invisible);
            arr.add(e);
        }
        JsonObject root = new JsonObject();
        root.add("frames", arr);
        try (FileWriter fw = new FileWriter(new File(dir, "frames.json"), StandardCharsets.UTF_8)) {
            GSON.toJson(root, fw);
        } catch (IOException ex) { LOGGER.error("[ItemMap] Failed to save frames.json", ex); }

        // Save custom images
        for (Map.Entry<String, byte[]> e : IMAGES.entrySet()) {
            try {
                Files.write(new File(new File(dir, "images"), e.getKey() + ".png").toPath(), e.getValue());
            } catch (IOException ex) { LOGGER.error("[ItemMap] Failed to save image {}", e.getKey(), ex); }
        }
    }

    public static void loadAll() {
        File dir      = getConfigDir();
        File jsonFile = new File(dir, "frames.json");
        if (!jsonFile.exists()) return;
        try {
            JsonObject root = JsonParser.parseReader(new FileReader(jsonFile, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray  arr  = root.getAsJsonArray("frames");
            for (JsonElement el : arr) {
                JsonObject e   = el.getAsJsonObject();
                long       eid = e.get("entityId").getAsLong();
                FrameData  d   = new FrameData(eid);
                try { d.mode = FrameData.DisplayMode.valueOf(e.get("mode").getAsString()); } catch (Exception ignored) {}
                d.spinSpeed     = e.has("spinSpeed")     ? e.get("spinSpeed").getAsFloat()      : 2f;
                d.scale         = e.has("scale")         ? e.get("scale").getAsFloat()           : 1f;
                d.padPct        = e.has("padPct")        ? e.get("padPct").getAsFloat()           : 0f;
                d.glowing       = e.has("glowing")       && e.get("glowing").getAsBoolean();
                String lbl      = e.has("label")         ? e.get("label").getAsString()           : "";
                d.label         = lbl.isEmpty() ? null : lbl;
                d.bgColor       = e.has("bgColor")       ? e.get("bgColor").getAsInt()            : 0;
                String imgId    = e.has("customImageId") ? e.get("customImageId").getAsString()   : "";
                d.customImageId = imgId.isEmpty() ? null : imgId;
                d.invisible     = e.has("invisible")     && e.get("invisible").getAsBoolean();
                FRAMES.put(eid, d);
            }
            LOGGER.info("[ItemMap] Loaded {} frame(s).", FRAMES.size());
        } catch (Exception ex) { LOGGER.error("[ItemMap] Failed to load frames.json", ex); }

        // Load custom images
        File imgDir = new File(dir, "images");
        if (imgDir.exists()) {
            File[] files = imgDir.listFiles((f, name) -> name.endsWith(".png"));
            if (files != null) {
                for (File f : files) {
                    String id = f.getName().replace(".png", "");
                    try { IMAGES.put(id, Files.readAllBytes(f.toPath())); }
                    catch (IOException ex) { LOGGER.error("[ItemMap] Failed to load image {}", id, ex); }
                }
            }
        }
    }
}
