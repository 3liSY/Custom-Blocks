package com.customblocks;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central data store for Custom Blocks v10.
 * Manages 512 block slots, recycle bin (50), undo history (20 steps),
 * per-face textures, animation frames, random texture variants, sounds, and templates.
 */
public class SlotManager {

    public static final int MAX_SLOTS     = 512;
    public static final int RECYCLE_SIZE  = 50;
    public static final int UNDO_DEPTH    = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger("CustomBlocks/SlotManager");
    private static final Gson   GSON   = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, SlotData> SLOTS      = new ConcurrentHashMap<>();
    private static final Map<String, String>   ID_TO_SLOT = new ConcurrentHashMap<>();

    /** Recycle bin: ordered newest-first, max RECYCLE_SIZE entries. */
    private static final List<SlotData> RECYCLE_BIN = Collections.synchronizedList(new ArrayList<>());

    /** Undo stack – stores snapshots as serialized JSON strings (lightweight). */
    private static final Deque<String> UNDO_STACK = new ArrayDeque<>();

    private static byte[] tabIconTexture = null;

    public static final List<String> FACE_KEYS = List.of("top","bottom","north","south","east","west");
    public static final List<String> SOUND_KEYS = List.of("stone","wood","grass","metal","glass","sand","wool","gravel");

    // ─────────────────────────────────────────────────────────────────────────
    // Data class
    // ─────────────────────────────────────────────────────────────────────────

    public static class SlotData {
        public final int    index;
        public final String customId;
        public       String displayName;
        public       byte[] texture;          // default (all-faces) texture
        public       int    lightLevel;       // 0-15
        public       float  hardness;         // mapped to 5 levels
        public       String soundType;        // stone/wood/grass/…
        public       boolean unbreakable;

        /** Per-face overrides. Keys: top bottom north south east west. */
        public final Map<String, byte[]> faceTextures = new ConcurrentHashMap<>();

        /** Animation frames.  Empty = not animated. */
        public final List<byte[]> animFrames = Collections.synchronizedList(new ArrayList<>());
        public       int    animFps   = 4;    // 1-20

        /** Random texture variants (up to 8). */
        public final List<byte[]> randomVariants = Collections.synchronizedList(new ArrayList<>());

        /** Custom step/break/place sound .ogg bytes – null = use soundType preset. */
        public byte[] soundBreak = null;
        public byte[] soundPlace = null;
        public byte[] soundStep  = null;

        public SlotData(int index, String customId, String displayName, byte[] texture,
                        int lightLevel, float hardness, String soundType) {
            this.index       = index;
            this.customId    = customId;
            this.displayName = displayName;
            this.texture     = texture;
            this.lightLevel  = Math.max(0, Math.min(15, lightLevel));
            this.hardness    = clampHardness(hardness);
            this.soundType   = (soundType != null && !soundType.isEmpty()) ? soundType : "stone";
        }

        public SlotData(int index, String customId, String displayName, byte[] texture) {
            this(index, customId, displayName, texture, 0, 1.5f, "stone");
        }

        public String  slotKey()   { return "slot_" + index; }
        public boolean hasFaces()  { return !faceTextures.isEmpty(); }
        public boolean isAnimated(){ return !animFrames.isEmpty(); }
        public boolean hasRandom() { return !randomVariants.isEmpty(); }
    }

    private static float clampHardness(float h) {
        // 5 levels: 0.3, 1.5, 3.0, 5.0, -1 (unbreakable)
        if (h <= 0) return -1f;
        if (h <= 1) return 0.3f;
        if (h <= 2) return 1.5f;
        if (h <= 4) return 3.0f;
        if (h <= 6) return 5.0f;
        return -1f;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────

    public static SlotData getBySlot(String slotKey)  { return SLOTS.get(slotKey); }
    public static SlotData getById(String customId)   { String k = ID_TO_SLOT.get(customId); return k != null ? SLOTS.get(k) : null; }
    public static Collection<SlotData> allSlots()     { return Collections.unmodifiableCollection(SLOTS.values()); }
    public static Set<String>  allCustomIds()         { return Collections.unmodifiableSet(ID_TO_SLOT.keySet()); }
    public static boolean      hasId(String id)       { return ID_TO_SLOT.containsKey(id); }
    public static int          usedSlots()            { return SLOTS.size(); }
    public static int          freeSlots()            { return MAX_SLOTS - SLOTS.size(); }
    public static byte[]       getTabIconTexture()    { return tabIconTexture; }
    public static void         setTabIconTexture(byte[] t) { tabIconTexture = t; }
    public static List<SlotData> getRecycleBin()      { return Collections.unmodifiableList(RECYCLE_BIN); }

    public static void clearAll() { SLOTS.clear(); ID_TO_SLOT.clear(); tabIconTexture = null; }

    // ─────────────────────────────────────────────────────────────────────────
    // Mutation  (all mutators push undo first)
    // ─────────────────────────────────────────────────────────────────────────

    public static SlotData assign(String customId, String displayName, byte[] texture) {
        pushUndo();
        for (int i = 0; i < MAX_SLOTS; i++) {
            String key = "slot_" + i;
            if (!SLOTS.containsKey(key)) {
                SlotData d = new SlotData(i, customId, displayName, texture);
                SLOTS.put(key, d);
                ID_TO_SLOT.put(customId, key);
                return d;
            }
        }
        return null; // all 512 used
    }

    public static boolean delete(String customId) {
        String key = ID_TO_SLOT.remove(customId);
        if (key == null) return false;
        pushUndo();
        SlotData removed = SLOTS.remove(key);
        if (removed != null) addToRecycle(removed);
        return true;
    }

    public static boolean rename(String customId, String newName) {
        SlotData d = getById(customId);
        if (d == null) return false;
        pushUndo();
        d.displayName = newName;
        return true;
    }

    public static boolean updateTexture(String customId, byte[] texture) {
        SlotData d = getById(customId);
        if (d == null) return false;
        pushUndo();
        d.texture = texture;
        return true;
    }

    public static boolean setFaceTexture(String customId, String face, byte[] texture) {
        SlotData d = getById(customId);
        if (d == null || !FACE_KEYS.contains(face)) return false;
        pushUndo();
        if (texture == null || texture.length == 0) d.faceTextures.remove(face);
        else d.faceTextures.put(face, texture);
        return true;
    }

    public static boolean setLight(String customId, int level) {
        SlotData d = getById(customId);
        if (d == null) return false;
        pushUndo();
        d.lightLevel = Math.max(0, Math.min(15, level));
        return true;
    }

    public static boolean setHardness(String customId, float h) {
        SlotData d = getById(customId);
        if (d == null) return false;
        pushUndo();
        d.hardness = clampHardness(h);
        return true;
    }

    public static boolean setSound(String customId, String sound) {
        SlotData d = getById(customId);
        if (d == null) return false;
        pushUndo();
        d.soundType = SOUND_KEYS.contains(sound) ? sound : "stone";
        return true;
    }

    public static boolean setCustomSound(String customId, String type, byte[] ogg) {
        SlotData d = getById(customId);
        if (d == null) return false;
        pushUndo();
        switch (type) {
            case "break" -> d.soundBreak = ogg;
            case "place" -> d.soundPlace = ogg;
            case "step"  -> d.soundStep  = ogg;
            default -> { return false; }
        }
        return true;
    }

    public static boolean setAnimation(String customId, List<byte[]> frames, int fps) {
        SlotData d = getById(customId);
        if (d == null) return false;
        pushUndo();
        d.animFrames.clear();
        if (frames != null) d.animFrames.addAll(frames);
        d.animFps = Math.max(1, Math.min(20, fps));
        return true;
    }

    public static boolean addRandomVariant(String customId, byte[] variant) {
        SlotData d = getById(customId);
        if (d == null) return false;
        if (d.randomVariants.size() >= 8) return false;
        pushUndo();
        d.randomVariants.add(variant);
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Recycle Bin
    // ─────────────────────────────────────────────────────────────────────────

    private static void addToRecycle(SlotData d) {
        RECYCLE_BIN.add(0, d);
        if (RECYCLE_BIN.size() > RECYCLE_SIZE) RECYCLE_BIN.remove(RECYCLE_BIN.size() - 1);
    }

    public static SlotData restoreFromRecycle(String customId) {
        for (int i = 0; i < RECYCLE_BIN.size(); i++) {
            SlotData d = RECYCLE_BIN.get(i);
            if (d.customId.equals(customId)) {
                RECYCLE_BIN.remove(i);
                if (SLOTS.containsKey(d.slotKey())) return null; // slot taken
                SLOTS.put(d.slotKey(), d);
                ID_TO_SLOT.put(d.customId, d.slotKey());
                return d;
            }
        }
        return null;
    }

    public static boolean purgeRecycle(String customId) {
        return RECYCLE_BIN.removeIf(d -> d.customId.equals(customId));
    }

    public static void clearRecycle() { RECYCLE_BIN.clear(); }

    // ─────────────────────────────────────────────────────────────────────────
    // Undo
    // ─────────────────────────────────────────────────────────────────────────

    private static void pushUndo() {
        String snap = serializeSlots();
        UNDO_STACK.addFirst(snap);
        if (UNDO_STACK.size() > UNDO_DEPTH) UNDO_STACK.removeLast();
    }

    public static boolean undo() {
        String snap = UNDO_STACK.pollFirst();
        if (snap == null) return false;
        clearAll();
        deserializeSlots(snap);
        return true;
    }

    public static int undoDepth() { return UNDO_STACK.size(); }

    // ─────────────────────────────────────────────────────────────────────────
    // Persistence (config/customblocks/)
    // ─────────────────────────────────────────────────────────────────────────

    private static final String CONFIG_DIR = "config/customblocks";

    public static void saveAll() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            // Save metadata JSON
            JsonArray arr = new JsonArray();
            for (SlotData d : SLOTS.values()) {
                JsonObject o = new JsonObject();
                o.addProperty("index",       d.index);
                o.addProperty("customId",    d.customId);
                o.addProperty("displayName", d.displayName);
                o.addProperty("lightLevel",  d.lightLevel);
                o.addProperty("hardness",    d.hardness);
                o.addProperty("soundType",   d.soundType);
                o.addProperty("unbreakable", d.unbreakable);
                o.addProperty("animFps",     d.animFps);
                arr.add(o);
            }
            Files.writeString(Paths.get(CONFIG_DIR, "blocks.json"), GSON.toJson(arr));
            // Save textures as separate binary files
            for (SlotData d : SLOTS.values()) {
                String base = CONFIG_DIR + "/tex_" + d.index;
                if (d.texture != null) Files.write(Paths.get(base + ".png"), d.texture);
                for (Map.Entry<String, byte[]> e : d.faceTextures.entrySet())
                    Files.write(Paths.get(base + "_face_" + e.getKey() + ".png"), e.getValue());
                for (int f = 0; f < d.animFrames.size(); f++)
                    Files.write(Paths.get(base + "_anim_" + f + ".png"), d.animFrames.get(f));
                for (int v = 0; v < d.randomVariants.size(); v++)
                    Files.write(Paths.get(base + "_rand_" + v + ".png"), d.randomVariants.get(v));
                if (d.soundBreak != null) Files.write(Paths.get(base + "_break.ogg"), d.soundBreak);
                if (d.soundPlace != null) Files.write(Paths.get(base + "_place.ogg"), d.soundPlace);
                if (d.soundStep  != null) Files.write(Paths.get(base + "_step.ogg"),  d.soundStep);
            }
            if (tabIconTexture != null) Files.write(Paths.get(CONFIG_DIR, "tab_icon.png"), tabIconTexture);
            LOGGER.info("[CustomBlocks] Saved {} blocks.", SLOTS.size());
        } catch (IOException e) {
            LOGGER.error("[CustomBlocks] Failed to save: {}", e.getMessage());
        }
    }

    public static void loadAll() {
        clearAll();
        Path blocksJson = Paths.get(CONFIG_DIR, "blocks.json");
        if (!Files.exists(blocksJson)) return;
        try {
            String json = Files.readString(blocksJson);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                int    idx   = o.get("index").getAsInt();
                String cid   = o.get("customId").getAsString();
                String name  = o.get("displayName").getAsString();
                int    light = o.has("lightLevel") ? o.get("lightLevel").getAsInt() : 0;
                float  hard  = o.has("hardness")   ? o.get("hardness").getAsFloat() : 1.5f;
                String snd   = o.has("soundType")  ? o.get("soundType").getAsString() : "stone";
                boolean unbr = o.has("unbreakable") && o.get("unbreakable").getAsBoolean();
                int    fps   = o.has("animFps")    ? o.get("animFps").getAsInt() : 4;

                byte[] tex = readFile(CONFIG_DIR + "/tex_" + idx + ".png");
                SlotData d  = new SlotData(idx, cid, name, tex, light, hard, snd);
                d.unbreakable = unbr;
                d.animFps     = fps;

                for (String face : FACE_KEYS) {
                    byte[] ft = readFile(CONFIG_DIR + "/tex_" + idx + "_face_" + face + ".png");
                    if (ft != null) d.faceTextures.put(face, ft);
                }
                for (int f = 0; ; f++) {
                    byte[] af = readFile(CONFIG_DIR + "/tex_" + idx + "_anim_" + f + ".png");
                    if (af == null) break;
                    d.animFrames.add(af);
                }
                for (int v = 0; ; v++) {
                    byte[] rv = readFile(CONFIG_DIR + "/tex_" + idx + "_rand_" + v + ".png");
                    if (rv == null) break;
                    d.randomVariants.add(rv);
                }
                d.soundBreak = readFile(CONFIG_DIR + "/tex_" + idx + "_break.ogg");
                d.soundPlace = readFile(CONFIG_DIR + "/tex_" + idx + "_place.ogg");
                d.soundStep  = readFile(CONFIG_DIR + "/tex_" + idx + "_step.ogg");

                SLOTS.put("slot_" + idx, d);
                ID_TO_SLOT.put(cid, "slot_" + idx);
            }
            tabIconTexture = readFile(CONFIG_DIR + "/tab_icon.png");
            LOGGER.info("[CustomBlocks] Loaded {} blocks.", SLOTS.size());
        } catch (Exception e) {
            LOGGER.error("[CustomBlocks] Load error: {}", e.getMessage());
        }
    }

    private static byte[] readFile(String path) {
        try { return Files.readAllBytes(Paths.get(path)); }
        catch (IOException e) { return null; }
    }

    // Minimal snapshot (no textures, just metadata — for undo)
    private static String serializeSlots() {
        JsonArray arr = new JsonArray();
        for (SlotData d : SLOTS.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("index", d.index); o.addProperty("customId", d.customId);
            o.addProperty("displayName", d.displayName); o.addProperty("lightLevel", d.lightLevel);
            o.addProperty("hardness", d.hardness); o.addProperty("soundType", d.soundType);
            arr.add(o);
        }
        return GSON.toJson(arr);
    }

    private static void deserializeSlots(String snap) {
        try {
            JsonArray arr = JsonParser.parseString(snap).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                int idx = o.get("index").getAsInt();
                // Re-read textures from disk for this index
                byte[] tex = readFile(CONFIG_DIR + "/tex_" + idx + ".png");
                SlotData d = new SlotData(idx,
                        o.get("customId").getAsString(),
                        o.get("displayName").getAsString(), tex,
                        o.get("lightLevel").getAsInt(),
                        o.get("hardness").getAsFloat(),
                        o.get("soundType").getAsString());
                SLOTS.put(d.slotKey(), d);
                ID_TO_SLOT.put(d.customId, d.slotKey());
            }
        } catch (Exception ignored) {}
    }
}
