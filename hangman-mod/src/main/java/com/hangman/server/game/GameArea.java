package com.hangman.server.game;

import com.google.gson.*;
import com.hangman.HangmanMod;
import net.minecraft.util.Identifier;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Named game areas / spawn points for Hangman. */
public class GameArea {

    public String name;
    public String dimension; // e.g. "minecraft:overworld"
    public double hangedX, hangedY, hangedZ;
    public float  hangedYaw, hangedPitch;
    public double hangerX, hangerY, hangerZ;
    public float  hangerYaw, hangerPitch;

    public GameArea() {}

    public GameArea(String name, String dim,
                    double hx, double hy, double hz, float hyaw, float hpitch,
                    double gx, double gy, double gz, float gyaw, float gpitch) {
        this.name = name; this.dimension = dim;
        this.hangedX = hx; this.hangedY = hy; this.hangedZ = hz;
        this.hangedYaw = hyaw; this.hangedPitch = hpitch;
        this.hangerX = gx; this.hangerY = gy; this.hangerZ = gz;
        this.hangerYaw = gyaw; this.hangerPitch = gpitch;
    }

    // ── registry ──────────────────────────────────────────────────────────────
    private static final Map<String, GameArea> AREAS = new LinkedHashMap<>();
    private static Path areasFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void setArea(GameArea area) {
        AREAS.put(area.name.toLowerCase(), area);
        save();
    }

    public static GameArea getArea(String name) {
        return AREAS.get(name.toLowerCase());
    }

    public static Collection<GameArea> getAllAreas() {
        return Collections.unmodifiableCollection(AREAS.values());
    }

    public static boolean deleteArea(String name) {
        boolean removed = AREAS.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public static void load(Path configDir) {
        areasFile = configDir.resolve("hangman_areas.json");
        if (!Files.exists(areasFile)) return;
        try (Reader r = new InputStreamReader(Files.newInputStream(areasFile), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                GameArea a = GSON.fromJson(e.getValue(), GameArea.class);
                if (a != null) AREAS.put(e.getKey(), a);
            }
        } catch (Exception e) {
            HangmanMod.LOGGER.error("[Hangman] Failed to load areas: {}", e.getMessage());
        }
    }

    private static void save() {
        if (areasFile == null) return;
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, GameArea> e : AREAS.entrySet()) {
                root.add(e.getKey(), GSON.toJsonTree(e.getValue()));
            }
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(areasFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
        } catch (Exception e) {
            HangmanMod.LOGGER.error("[Hangman] Failed to save areas: {}", e.getMessage());
        }
    }
}
