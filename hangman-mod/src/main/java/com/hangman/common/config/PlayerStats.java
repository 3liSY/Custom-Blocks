package com.hangman.common.config;

import com.google.gson.*;
import com.hangman.HangmanMod;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Persistent per-player Hangman stats. */
public class PlayerStats {

    public int wins        = 0;
    public int losses      = 0;
    public int gamesPlayed = 0;
    public int winStreak   = 0;
    public int bestStreak  = 0;

    // ── static registry ───────────────────────────────────────────────────────
    private static final Map<UUID, PlayerStats> STATS = new LinkedHashMap<>();
    private static Path statsFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static PlayerStats of(UUID uuid) {
        return STATS.computeIfAbsent(uuid, k -> new PlayerStats());
    }

    public static List<Map.Entry<UUID, PlayerStats>> leaderboard() {
        List<Map.Entry<UUID, PlayerStats>> list = new ArrayList<>(STATS.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue().wins, a.getValue().wins));
        return list;
    }

    public static void load(Path configDir) {
        statsFile = configDir.resolve("hangman_stats.json");
        if (!Files.exists(statsFile)) return;
        try (Reader r = new InputStreamReader(Files.newInputStream(statsFile), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(e.getKey());
                    PlayerStats ps = GSON.fromJson(e.getValue(), PlayerStats.class);
                    if (ps != null) STATS.put(uuid, ps);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception e) {
            HangmanMod.LOGGER.error("[Hangman] Failed to load stats: {}", e.getMessage());
        }
    }

    public static void save() {
        if (statsFile == null) return;
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, PlayerStats> e : STATS.entrySet()) {
                root.add(e.getKey().toString(), GSON.toJsonTree(e.getValue()));
            }
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(statsFile), StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
        } catch (Exception e) {
            HangmanMod.LOGGER.error("[Hangman] Failed to save stats: {}", e.getMessage());
        }
    }

    public void recordWin() {
        wins++;
        gamesPlayed++;
        winStreak++;
        if (winStreak > bestStreak) bestStreak = winStreak;
        save();
    }

    public void recordLoss() {
        losses++;
        gamesPlayed++;
        winStreak = 0;
        save();
    }
}
