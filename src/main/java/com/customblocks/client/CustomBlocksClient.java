package com.customblocks.client;

import com.customblocks.CustomBlocksMod;
import com.customblocks.RegistryUtils;
import com.customblocks.block.CustomBlock;
import com.customblocks.network.CustomBlockListPayload;
import com.customblocks.network.CustomBlockSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Environment(EnvType.CLIENT)
public class CustomBlocksClient implements ClientModInitializer {

    private static final String PACK_ENTRY = "file/customblocks_generated";

    @Override
    public void onInitializeClient() {
        // Load blocks saved from last server session
        CustomBlocksMod.loadBlocksFromConfig();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ResourcePackGenerator.generate();
            injectPackIfNeeded(client);
        });

        // Server sends us the list of blocks it has — clean up any stale local ones
        ClientPlayNetworking.registerGlobalReceiver(CustomBlockListPayload.ID,
            (payload, context) -> {
                List<String> serverIds = payload.blockIds();
                context.client().execute(() -> cleanStaleLocalBlocks(context.client(), serverIds));
            }
        );

        // Server sends us a block texture — save it locally for next restart
        ClientPlayNetworking.registerGlobalReceiver(CustomBlockSyncPayload.ID,
            (payload, context) -> {
                String blockId     = payload.blockId();
                String displayName = payload.displayName();
                byte[] texture     = payload.textureData();
                context.client().execute(() -> {
                    try {
                        saveBlockLocally(context.client(), blockId, displayName, texture);
                    } catch (Exception e) {
                        CustomBlocksMod.LOGGER.error("[CustomBlocks] Error saving block '{}'", blockId, e);
                    }
                });
            }
        );
    }

    /**
     * Delete local config folders for blocks the server no longer has.
     * These would cause registry mismatch on next restart.
     */
    private static void cleanStaleLocalBlocks(MinecraftClient client, List<String> serverIds) {
        File configDir = new File(client.runDirectory, "config/customblocks");
        if (!configDir.exists()) return;
        File[] folders = configDir.listFiles(File::isDirectory);
        if (folders == null) return;

        boolean changed = false;
        for (File folder : folders) {
            String blockId = folder.getName().toLowerCase().replaceAll("[^a-z0-9_]", "_");
            if (!serverIds.contains(blockId)) {
                deleteFolder(folder);
                CustomBlocksMod.LOGGER.info("[CustomBlocks] Removed stale local block: {}", blockId);
                changed = true;
            }
        }

        if (changed) {
            ResourcePackGenerator.generate();
        }
    }

    /**
     * Save block texture to local config so it loads on next client restart.
     * Also register it dynamically for this session's resource pack.
     */
    private static void saveBlockLocally(MinecraftClient client, String blockId,
                                          String displayName, byte[] textureBytes) throws IOException {
        File mcDir = client.runDirectory;
        File blockFolder = new File(mcDir, "config/customblocks/" + blockId);
        blockFolder.mkdirs();
        File textureFile = new File(blockFolder, "texture.png");
        Files.write(textureFile.toPath(), textureBytes);
        Files.writeString(new File(blockFolder, "name.txt").toPath(), displayName);

        // Tab icon — just update texture, no need to re-register
        if (blockId.equals(CustomBlocksMod.TAB_ICON_ID)) {
            CustomBlocksMod.BLOCK_TEXTURES.put(CustomBlocksMod.TAB_ICON_ID, textureFile);
            if (CustomBlocksMod.TAB_ICON_BLOCK == null) {
                CustomBlocksMod.registerTabIconBlock(blockFolder, textureFile, true);
            }
            ResourcePackGenerator.generate();
            injectPackIfNeeded(client);
            client.reloadResources();
            return;
        }

        // Regular block — only register dynamically if not already registered
        if (!CustomBlocksMod.CUSTOM_BLOCKS.containsKey(blockId)) {
            Identifier id = Identifier.of(CustomBlocksMod.MOD_ID, blockId);
            SimpleRegistry<net.minecraft.block.Block> blockReg =
                (SimpleRegistry<net.minecraft.block.Block>) Registries.BLOCK;
            SimpleRegistry<Item> itemReg = (SimpleRegistry<Item>) Registries.ITEM;

            RegistryUtils.unfreeze(blockReg);
            CustomBlock block = new CustomBlock(displayName, AbstractBlock.Settings.create().strength(1.5f, 6.0f));
            Registry.register(Registries.BLOCK, id, block);
            RegistryUtils.freeze(blockReg);

            RegistryUtils.unfreeze(itemReg);
            Registry.register(Registries.ITEM, id, new CustomBlock.CustomBlockItem(block, new Item.Settings()));
            RegistryUtils.freeze(itemReg);

            CustomBlocksMod.CUSTOM_BLOCKS.put(blockId, block);
        }

        CustomBlocksMod.BLOCK_TEXTURES.put(blockId, textureFile);
        ResourcePackGenerator.generate();
        injectPackIfNeeded(client);
        client.reloadResources();
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) for (File f : files) f.delete();
        folder.delete();
    }

    private static void injectPackIfNeeded(MinecraftClient client) {
        if (!client.options.resourcePacks.contains(PACK_ENTRY)) {
            client.options.resourcePacks.add(PACK_ENTRY);
            client.options.write();
        }
    }
}
