package com.customblocks.client;

import com.customblocks.CustomBlocksMod;
import com.customblocks.SlotManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@Environment(EnvType.CLIENT)
public class ResourcePackGenerator {

    private static final Gson   GSON        = new GsonBuilder().setPrettyPrinting().create();
    private static final int    PACK_FORMAT = 34;
    private static final String MOD_ID      = CustomBlocksMod.MOD_ID;

    public static void generate(MinecraftClient client) {
        try {
            File mcDir    = client.runDirectory;
            File packRoot = new File(mcDir, "resourcepacks/customblocks_generated");
            File assets   = new File(packRoot, "assets/" + MOD_ID);

            new File(assets, "blockstates").mkdirs();
            new File(assets, "models/block").mkdirs();
            new File(assets, "models/item").mkdirs();
            new File(assets, "textures/block").mkdirs();
            new File(assets, "textures/item").mkdirs();

            // pack.mcmeta
            JsonObject pack = new JsonObject(); pack.addProperty("pack_format", PACK_FORMAT);
            pack.addProperty("description", "CustomBlocks Generated");
            JsonObject meta = new JsonObject(); meta.add("pack", pack);
            writeJson(meta, new File(packRoot, "pack.mcmeta"));

            for (int i = 0; i < SlotManager.MAX_SLOTS; i++) {
                String slotKey = "slot_" + i;
                SlotManager.SlotData data = SlotManager.getBySlot(slotKey);
                String texRef   = MOD_ID + ":block/" + slotKey;
                String modelRef = MOD_ID + ":block/" + slotKey;

                // Texture
                File texDest = new File(assets, "textures/block/" + slotKey + ".png");
                if (data != null && data.texture != null && data.texture.length > 0) {
                    Files.write(texDest.toPath(), data.texture);
                } else {
                    Files.write(texDest.toPath(), PLACEHOLDER_PNG);
                }

                // Blockstate
                JsonObject variant = new JsonObject(); variant.addProperty("model", modelRef);
                JsonObject variants = new JsonObject(); variants.add("", variant);
                JsonObject bs = new JsonObject(); bs.add("variants", variants);
                writeJson(bs, new File(assets, "blockstates/" + slotKey + ".json"));

                // Block model
                JsonObject tex = new JsonObject(); tex.addProperty("all", texRef);
                JsonObject bm = new JsonObject();
                bm.addProperty("parent", "minecraft:block/cube_all");
                bm.add("textures", tex);
                writeJson(bm, new File(assets, "models/block/" + slotKey + ".json"));

                // Item model
                JsonObject im = new JsonObject(); im.addProperty("parent", modelRef);
                writeJson(im, new File(assets, "models/item/" + slotKey + ".json"));
            }

            // Tab icon
            byte[] tabIcon = SlotManager.getTabIconTexture();
            File tabDest = new File(assets, "textures/item/tab_icon.png");
            Files.write(tabDest.toPath(), tabIcon != null && tabIcon.length > 0 ? tabIcon : PLACEHOLDER_PNG);

            CustomBlocksMod.LOGGER.info("[CustomBlocks] Resource pack generated.");
        } catch (Exception e) {
            CustomBlocksMod.LOGGER.error("[CustomBlocks] Failed to generate resource pack", e);
        }
    }

    private static void writeJson(JsonObject json, File dest) throws IOException {
        dest.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(dest, StandardCharsets.UTF_8)) { GSON.toJson(json, fw); }
    }

    // 1×1 opaque pink placeholder PNG
    private static final byte[] PLACEHOLDER_PNG = {
        (byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,
        0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,0x08,0x02,0x00,0x00,0x00,(byte)0x90,0x77,0x53,(byte)0xDE,
        0x00,0x00,0x00,0x0C,0x49,0x44,0x41,0x54,0x08,(byte)0xD7,0x63,(byte)0xF8,(byte)0x0F,(byte)0xF0,
        0x00,0x00,0x00,0x02,0x00,0x01,(byte)0xE2,0x21,(byte)0xBC,0x33,0x00,0x00,0x00,0x00,
        0x49,0x45,0x4E,0x44,(byte)0xAE,0x42,0x60,(byte)0x82
    };
}
