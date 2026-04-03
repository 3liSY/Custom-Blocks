package com.customblocks.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of block slot metadata.
 * Populated from FullSyncPayload + SlotUpdatePayload packets.
 */
@Environment(EnvType.CLIENT)
public class ClientSlotData {

    public final int    index;
    public final String customId;
    public       String displayName;
    public       int    lightLevel;
    public       float  hardness;
    public       String soundType;
    public       boolean hasTexture;

    // Favorites are stored client-side only
    private static final Set<String> FAVORITES = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<String, ClientSlotData> BY_ID   = new ConcurrentHashMap<>();
    private static final Map<Integer, ClientSlotData> BY_IDX = new ConcurrentHashMap<>();

    public ClientSlotData(int index, String customId, String displayName,
                          int lightLevel, float hardness, String soundType) {
        this.index       = index;
        this.customId    = customId;
        this.displayName = displayName;
        this.lightLevel  = lightLevel;
        this.hardness    = hardness;
        this.soundType   = soundType;
    }

    // ── Registry ──────────────────────────────────────────────────────────────

    public static void put(ClientSlotData d) {
        BY_ID.put(d.customId, d);
        BY_IDX.put(d.index, d);
    }

    public static void remove(String customId) {
        ClientSlotData d = BY_ID.remove(customId);
        if (d != null) BY_IDX.remove(d.index);
        FAVORITES.remove(customId);
    }

    public static ClientSlotData getById(String id)     { return BY_ID.get(id); }
    public static ClientSlotData getByIndex(int idx)    { return BY_IDX.get(idx); }
    public static Collection<ClientSlotData> all()      { return Collections.unmodifiableCollection(BY_ID.values()); }
    public static void clear() { BY_ID.clear(); BY_IDX.clear(); }

    // ── Favorites ─────────────────────────────────────────────────────────────

    public static void toggleFavorite(String id) {
        if (FAVORITES.contains(id)) FAVORITES.remove(id);
        else FAVORITES.add(id);
    }
    public static boolean isFavorite(String id) { return FAVORITES.contains(id); }
    public static Set<String> favorites()       { return Collections.unmodifiableSet(FAVORITES); }
}
