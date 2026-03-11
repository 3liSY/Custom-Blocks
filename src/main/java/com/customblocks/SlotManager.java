package com.customblocks;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SlotManager {

    public static final int MAX_SLOTS = 512;
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomBlocks/SlotManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, SlotData> SLOTS     = new ConcurrentHashMap<>();
    private static final Map<String, String>   ID_TO_SLOT = new ConcurrentHashMap<>();
    private static byte[] tabIconTexture = null;

    // ── Data class ────────────────────────────────────────────────────────────

    public static class SlotData {
        public final int    index;
        public final String customId;
        public final String displayName;
        public       byte[] texture;
        public       int    lightLevel;   // 0-15
        public       float  hardness;     // 0=instant, negative=unbreakable
        public       String soundType;    // stone|wood|grass|metal|glass|sand|wool

        public SlotData(int index, String customId, String displayName, byte[] texture,
                        int lightLevel, float hardness, String soundType) {
            this.index       = index;
            this.customId    = customId;
            this.displayName = displayName;
            this.texture     = texture;
            this.lightLevel  = Math.max(0, Math.min(15, lightLevel));
            this.hardness    = hardness;
            this.soundType   = (soundType != null && !soundType.isEmpty()) ? soundType : "stone";
        }

        /** Convenience: default properties */
        public SlotData(int index, String customId, String displayName, byte[] texture) {
            this(index, customId, displayName, texture, 0, 1.5f, "stone");
        }

        public String slotKey() { return "slot_" + index; }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public static SlotData getBySlot(String slotKey)  { return SLOTS.get(slotKey); }
    public static SlotData getById(String customId) {
        String k = ID_TO_SLOT.get(customId);
        return k != null ? SLOTS.get(k) : null;
    }
    public static Collection<SlotData> allSlots()    { return Collections.unmodifiableCollection(SLOTS.values()); }
    public static Set<String>          allCustomIds() { return Collections.unmodifiableSet(ID_TO_SLOT.keySet()); }
    public static boolean              hasId(String id) { return ID_TO_SLOT.containsKey(id); }
    public static int                  usedSlots()    { return SLOTS.size(); }
    public static int                  freeSlots()    { return MAX_SLOTS - SLOTS.size(); }
    public static byte[]               getTabIconTexture() { return tabIconTexture; }
    public static void                 setTabIconTexture(byte[] t) { tabIconTexture = t; }

    /** Clear all in-memory slot data (used by client before re-syncing from server). */
    public static void clearAll() {
        SLOTS.clear();
        ID_TO_SLOT.clear();
        tabIconTexture = null;
    }

    public static String getDisplayName(String slotKey) {
        SlotData d = SLOTS.get(slotKey);
        return d != null ? d.displayName : null;
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    public static SlotData assign(String customId, String displayName, byte[] texture) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            String key = "slot_" + i;
            if (!SLOTS.containsKey(key)) {
                SlotData data = new SlotData(i, customId, displayName, texture);
                SLOTS.put(key, data);
                ID_TO_SLOT.put(customId, key);
                return data;
            }
        }
        return null;
    }

    /** Assign a block to a SPECIFIC slot index (used by client to mirror server's mapping exactly). */
    public static SlotData assignAtIndex(int index, String customId, String displayName, byte[] texture) {
        if (index < 0 || index >= MAX_SLOTS) return null;
        String key = "slot_" + index;
        SlotData data = new SlotData(index, customId, displayName, texture);
        SLOTS.put(key, data);
        ID_TO_SLOT.put(customId, key);
        return data;
    }


    public static boolean remove(String customId) {
        String k = ID_TO_SLOT.remove(customId);
        if (k == null) return false;
        SLOTS.remove(k);
        return true;
    }

    public static boolean rename(String customId, String newName) {
        String k = ID_TO_SLOT.get(customId);
        if (k == null) return false;
        SlotData o = SLOTS.get(k);
        SLOTS.put(k, new SlotData(o.index, o.customId, newName, o.texture,
                o.lightLevel, o.hardness, o.soundType));
        return true;
    }

    public static boolean updateTexture(String customId, byte[] texture) {
        String k = ID_TO_SLOT.get(customId);
        if (k == null) return false;
        SlotData o = SLOTS.get(k);
        SLOTS.put(k, new SlotData(o.index, o.customId, o.displayName, texture,
                o.lightLevel, o.hardness, o.soundType));
        return true;
    }

    public static boolean setProperties(String customId, int lightLevel, float hardness, String soundType) {
        String k = ID_TO_SLOT.get(customId);
        if (k == null) return false;
        SlotData o = SLOTS.get(k);
        SLOTS.put(k, new SlotData(o.index, o.customId, o.displayName, o.texture,
                lightLevel, hardness, soundType));
        return true;
    }

    public static boolean setLightLevel(String customId, int level) {
        String k = ID_TO_SLOT.get(customId);
        if (k == null) return false;
        SlotData o = SLOTS.get(k);
        SLOTS.put(k, new SlotData(o.index, o.customId, o.displayName, o.texture,
                level, o.hardness, o.soundType));
        return true;
    }

    public static boolean setHardness(String customId, float hardness) {
        String k = ID_TO_SLOT.get(customId);
        if (k == null) return false;
        SlotData o = SLOTS.get(k);
        SLOTS.put(k, new SlotData(o.index, o.customId, o.displayName, o.texture,
                o.lightLevel, hardness, o.soundType));
        return true;
    }

    public static boolean setSoundType(String customId, String soundType) {
        String k = ID_TO_SLOT.get(customId);
        if (k == null) return false;
        SlotData o = SLOTS.get(k);
        SLOTS.put(k, new SlotData(o.index, o.customId, o.displayName, o.texture,
                o.lightLevel, o.hardness, soundType));
        return true;
    }

    // ── Persistence (server-side) ─────────────────────────────────────────────

    private static File getConfigDir() { return new File("config/customblocks"); }

    public static void saveAll() {
        File dir = getConfigDir();
        dir.mkdirs();
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (SlotData d : SLOTS.values()) {
            JsonObject e = new JsonObject();
            e.addProperty("index",       d.index);
            e.addProperty("customId",    d.customId);
            e.addProperty("displayName", d.displayName);
            e.addProperty("lightLevel",  d.lightLevel);
            e.addProperty("hardness",    d.hardness);
            e.addProperty("soundType",   d.soundType);
            arr.add(e);
        }
        root.add("slots", arr);
        try (FileWriter fw = new FileWriter(new File(dir, "slots.json"), StandardCharsets.UTF_8)) {
            GSON.toJson(root, fw);
        } catch (IOException ex) { LOGGER.error("Failed to save slots.json", ex); }

        for (SlotData d : SLOTS.values()) {
            if (d.texture != null) {
                try { Files.write(new File(dir, d.slotKey() + ".png").toPath(), d.texture); }
                catch (IOException ex) { LOGGER.error("Failed to save texture for {}", d.customId, ex); }
            }
        }
        if (tabIconTexture != null) {
            try { Files.write(new File(dir, "tab_icon.png").toPath(), tabIconTexture); }
            catch (IOException ex) { LOGGER.error("Failed to save tab icon", ex); }
        }
    }

    public static void loadAll() {
        File dir = getConfigDir();
        if (!dir.exists()) return;
        File slotsFile = new File(dir, "slots.json");
        if (!slotsFile.exists()) return;
        try {
            JsonObject root = JsonParser.parseReader(new FileReader(slotsFile, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray slots = root.getAsJsonArray("slots");
            for (JsonElement el : slots) {
                JsonObject e = el.getAsJsonObject();
                int    index       = e.get("index").getAsInt();
                String customId    = e.get("customId").getAsString();
                String displayName = e.get("displayName").getAsString();
                int    lightLevel  = e.has("lightLevel")  ? e.get("lightLevel").getAsInt()    : 0;
                float  hardness    = e.has("hardness")    ? e.get("hardness").getAsFloat()    : 1.5f;
                String soundType   = e.has("soundType")   ? e.get("soundType").getAsString()  : "stone";
                File   texFile     = new File(dir, "slot_" + index + ".png");
                byte[] texture     = texFile.exists() ? Files.readAllBytes(texFile.toPath()) : null;
                SlotData data = new SlotData(index, customId, displayName, texture,
                        lightLevel, hardness, soundType);
                SLOTS.put("slot_" + index, data);
                ID_TO_SLOT.put(customId, "slot_" + index);
            }
            LOGGER.info("[CustomBlocks] Loaded {} slot(s).", SLOTS.size());
        } catch (Exception ex) { LOGGER.error("Failed to load slots.json", ex); }

        File tabFile = new File(dir, "tab_icon.png");
        if (tabFile.exists()) {
            try { tabIconTexture = Files.readAllBytes(tabFile.toPath()); }
            catch (IOException ex) { LOGGER.error("Failed to load tab icon", ex); }
        }
    }

    // ── Client-side persistence ───────────────────────────────────────────────

    public static void loadFromClientDir(File mcDir) {
        File dir = new File(mcDir, "config/customblocks");
        if (!dir.exists()) return;
        File slotsFile = new File(dir, "slots.json");
        if (!slotsFile.exists()) return;
        try {
            SLOTS.clear();
            ID_TO_SLOT.clear();
            JsonObject root = JsonParser.parseReader(new FileReader(slotsFile, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray slots = root.getAsJsonArray("slots");
            for (JsonElement el : slots) {
                JsonObject e = el.getAsJsonObject();
                int    index       = e.get("index").getAsInt();
                String customId    = e.get("customId").getAsString();
                String displayName = e.get("displayName").getAsString();
                int    lightLevel  = e.has("lightLevel")  ? e.get("lightLevel").getAsInt()    : 0;
                float  hardness    = e.has("hardness")    ? e.get("hardness").getAsFloat()    : 1.5f;
                String soundType   = e.has("soundType")   ? e.get("soundType").getAsString()  : "stone";
                File   texFile     = new File(dir, "slot_" + index + ".png");
                byte[] texture     = texFile.exists() ? Files.readAllBytes(texFile.toPath()) : null;
                SlotData data = new SlotData(index, customId, displayName, texture,
                        lightLevel, hardness, soundType);
                SLOTS.put("slot_" + index, data);
                ID_TO_SLOT.put(customId, "slot_" + index);
            }
            File tabFile = new File(dir, "tab_icon.png");
            if (tabFile.exists()) tabIconTexture = Files.readAllBytes(tabFile.toPath());
        } catch (Exception ex) { LOGGER.error("[CustomBlocks] Client failed to load slots", ex); }
    }

    public static void saveToClientDir(File mcDir) {
        File dir = new File(mcDir, "config/customblocks");
        dir.mkdirs();
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (SlotData d : SLOTS.values()) {
            JsonObject e = new JsonObject();
            e.addProperty("index",       d.index);
            e.addProperty("customId",    d.customId);
            e.addProperty("displayName", d.displayName);
            e.addProperty("lightLevel",  d.lightLevel);
            e.addProperty("hardness",    d.hardness);
            e.addProperty("soundType",   d.soundType);
            arr.add(e);
        }
        root.add("slots", arr);
        try (FileWriter fw = new FileWriter(new File(dir, "slots.json"), StandardCharsets.UTF_8)) {
            GSON.toJson(root, fw);
        } catch (IOException ex) { LOGGER.error("Failed to write client slots.json", ex); }
        for (SlotData d : SLOTS.values()) {
            if (d.texture != null) {
                try { Files.write(new File(dir, d.slotKey() + ".png").toPath(), d.texture); }
                catch (IOException ignored) {}
            }
        }
        if (tabIconTexture != null) {
            try { Files.write(new File(dir, "tab_icon.png").toPath(), tabIconTexture); }
            catch (IOException ignored) {}
        }
    }
}
