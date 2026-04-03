package com.customblocks;

import com.google.gson.*;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Permission manager for Custom Blocks v10.
 * Reads config/customblocks/permissions.json
 * Levels: browse | give | edit | admin
 */
public class PermissionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("CustomBlocks/Permissions");
    private static final String PATH   = "config/customblocks/permissions.json";
    private static final Gson   GSON   = new GsonBuilder().setPrettyPrinting().create();

    public enum Level { BROWSE, GIVE, EDIT, ADMIN }

    private static Map<String, Level> playerLevels   = new HashMap<>();
    private static Map<String, Level> groupLevels    = new HashMap<>();
    private static Level              defaultLevel    = Level.BROWSE;
    private static int                opPermLevel     = 2;  // OP level that grants ADMIN

    public static void load() {
        Path p = Paths.get(PATH);
        if (!Files.exists(p)) {
            createDefault();
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            if (root.has("default")) defaultLevel = parseLevel(root.get("default").getAsString());
            if (root.has("opLevel")) opPermLevel   = root.get("opLevel").getAsInt();
            playerLevels.clear();
            if (root.has("players")) {
                root.getAsJsonObject("players").entrySet().forEach(e ->
                        playerLevels.put(e.getKey(), parseLevel(e.getValue().getAsString())));
            }
            groupLevels.clear();
            if (root.has("groups")) {
                root.getAsJsonObject("groups").entrySet().forEach(e ->
                        groupLevels.put(e.getKey(), parseLevel(e.getValue().getAsString())));
            }
            LOGGER.info("[CustomBlocks] Permissions loaded.");
        } catch (Exception e) {
            LOGGER.error("[CustomBlocks] Permission load error: {}", e.getMessage());
        }
    }

    private static void createDefault() {
        try {
            Files.createDirectories(Paths.get("config/customblocks"));
            JsonObject root = new JsonObject();
            root.addProperty("default",  "browse");
            root.addProperty("opLevel",  2);
            JsonObject players = new JsonObject();
            players.addProperty("ExampleAdmin", "admin");
            players.addProperty("ExampleEditor", "edit");
            root.add("players", players);
            JsonObject groups = new JsonObject();
            groups.addProperty("admin", "admin");
            root.add("groups", groups);
            Files.writeString(Paths.get(PATH), GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("[CustomBlocks] Could not write default permissions.json");
        }
    }

    public static Level getLevel(ServerPlayerEntity player) {
        if (player.hasPermissionLevel(opPermLevel)) return Level.ADMIN;
        String name = player.getName().getString();
        if (playerLevels.containsKey(name)) return playerLevels.get(name);
        return defaultLevel;
    }

    public static boolean canBrowse(ServerPlayerEntity p) { return getLevel(p).ordinal() >= Level.BROWSE.ordinal(); }
    public static boolean canGive(ServerPlayerEntity p)   { return getLevel(p).ordinal() >= Level.GIVE.ordinal(); }
    public static boolean canEdit(ServerPlayerEntity p)   { return getLevel(p).ordinal() >= Level.EDIT.ordinal(); }
    public static boolean isAdmin(ServerPlayerEntity p)   { return getLevel(p) == Level.ADMIN; }

    private static Level parseLevel(String s) {
        return switch (s.toLowerCase()) {
            case "admin"  -> Level.ADMIN;
            case "edit"   -> Level.EDIT;
            case "give"   -> Level.GIVE;
            default       -> Level.BROWSE;
        };
    }
}
