package com.customblocks.client;

import com.customblocks.CustomBlocksMod;
import com.customblocks.block.CustomBlock;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ResourcePackGenerator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int PACK_FORMAT = 34;

    public static void generate() {
        if (CustomBlocksMod.CUSTOM_BLOCKS.isEmpty()) return;

        try {
            // Use the actual Minecraft run directory so Lunar Client works too
            File mcDir = MinecraftClient.getInstance().runDirectory;
            File packRoot   = new File(mcDir, "resourcepacks/customblocks_generated");
            File assetsRoot = new File(packRoot, "assets/" + CustomBlocksMod.MOD_ID);

            new File(assetsRoot, "blockstates").mkdirs();
            new File(assetsRoot, "models/block").mkdirs();
            new File(assetsRoot, "models/item").mkdirs();
            new File(assetsRoot, "textures/block").mkdirs();

            JsonObject packInfo = new JsonObject();
            packInfo.addProperty("pack_format", PACK_FORMAT);
            packInfo.addProperty("description", "CustomBlocks Generated Resources");
            JsonObject meta = new JsonObject();
            meta.add("pack", packInfo);
            writeJson(meta, new File(packRoot, "pack.mcmeta"));

            for (Map.Entry<String, CustomBlock> entry : CustomBlocksMod.CUSTOM_BLOCKS.entrySet()) {
                String blockId    = entry.getKey();
                File   textureSrc = CustomBlocksMod.BLOCK_TEXTURES.get(blockId);
                if (textureSrc == null || !textureSrc.exists()) continue;

                String modId      = CustomBlocksMod.MOD_ID;
                String textureRef = modId + ":block/" + blockId;
                String modelRef   = modId + ":block/" + blockId;

                Files.copy(textureSrc.toPath(),
                        new File(assetsRoot, "textures/block/" + blockId + ".png").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);

                JsonObject variant = new JsonObject();
                variant.addProperty("model", modelRef);
                JsonObject variants = new JsonObject();
                variants.add("", variant);
                JsonObject blockstate = new JsonObject();
                blockstate.add("variants", variants);
                writeJson(blockstate, new File(assetsRoot, "blockstates/" + blockId + ".json"));

                JsonObject textures = new JsonObject();
                textures.addProperty("all", textureRef);
                JsonObject blockModel = new JsonObject();
                blockModel.addProperty("parent", "minecraft:block/cube_all");
                blockModel.add("textures", textures);
                writeJson(blockModel, new File(assetsRoot, "models/block/" + blockId + ".json"));

                JsonObject itemModel = new JsonObject();
                itemModel.addProperty("parent", modelRef);
                writeJson(itemModel, new File(assetsRoot, "models/item/" + blockId + ".json"));
            }

            CustomBlocksMod.LOGGER.info("[CustomBlocks] Resource pack generated at: {}", packRoot.getAbsolutePath());

        } catch (Exception e) {
            CustomBlocksMod.LOGGER.error("[CustomBlocks] Failed to generate resource pack!", e);
        }
    }

    private static void writeJson(JsonObject json, File dest) throws IOException {
        dest.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(dest)) {
            GSON.toJson(json, fw);
        }
    }
}
