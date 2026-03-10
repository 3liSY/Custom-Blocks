package com.customblocks.client;

import com.customblocks.CustomBlocksMod;
import com.customblocks.RegistryUtils;
import com.customblocks.block.CustomBlock;
import com.customblocks.network.CustomBlockSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Environment(EnvType.CLIENT)
public class CustomBlocksClient implements ClientModInitializer {

    private static final String PACK_ENTRY = "file/customblocks_generated";

    @Override
    public void onInitializeClient() {
        // Generate resource pack for blocks that existed at startup
        if (!CustomBlocksMod.CUSTOM_BLOCKS.isEmpty()) {
            ResourcePackGenerator.generate();
        }

        // Inject pack once on first launch
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (!CustomBlocksMod.CUSTOM_BLOCKS.isEmpty()) {
                injectPackIfNeeded(client);
            }
        });

        // Handle the sync packet from the server — this is what makes blocks
        // appear instantly without any restart
        ClientPlayNetworking.registerGlobalReceiver(CustomBlockSyncPayload.ID,
                (payload, context) -> {
                    String blockId     = payload.blockId();
                    String displayName = payload.displayName();
                    byte[] texture     = payload.textureData();

                    context.client().execute(() -> {
                        try {
                            handleNewBlock(context.client(), blockId, displayName, texture);
                        } catch (Exception e) {
                            CustomBlocksMod.LOGGER.error(
                                "[CustomBlocks] Failed to handle sync packet for '{}'", blockId, e);
                        }
                    });
                }
        );
    }

    /**
     * Called on the client game thread when the server sends a new block.
     * Registers the block locally, writes assets, and reloads resources.
     */
    private static void handleNewBlock(MinecraftClient client,
                                        String blockId, String displayName, byte[] textureBytes)
            throws IOException {

        // Skip if already registered (e.g. same session second call)
        if (CustomBlocksMod.CUSTOM_BLOCKS.containsKey(blockId)) return;

        // 1. Write texture to config folder so ResourcePackGenerator can find it
        File blockFolder = new File("config/customblocks/" + blockId);
        blockFolder.mkdirs();
        File textureFile = new File(blockFolder, "texture.png");
        Files.write(textureFile.toPath(), textureBytes);
        Files.writeString(new File(blockFolder, "name.txt").toPath(), displayName);

        // 2. Register block + item on the CLIENT registry
        CustomBlock block = new CustomBlock(displayName, AbstractBlock.Settings.create().strength(1.5f, 6.0f));
        BlockItem  blockItem = new BlockItem(block, new Item.Settings());

        SimpleRegistry<net.minecraft.block.Block> blockReg =
                (SimpleRegistry<net.minecraft.block.Block>) Registries.BLOCK;
        SimpleRegistry<Item> itemReg = (SimpleRegistry<Item>) Registries.ITEM;

        RegistryUtils.unfreeze(blockReg);
        Registry.register(Registries.BLOCK, Identifier.of(CustomBlocksMod.MOD_ID, blockId), block);
        RegistryUtils.freeze(blockReg);

        RegistryUtils.unfreeze(itemReg);
        Registry.register(Registries.ITEM, Identifier.of(CustomBlocksMod.MOD_ID, blockId), blockItem);
        RegistryUtils.freeze(itemReg);

        CustomBlocksMod.CUSTOM_BLOCKS.put(blockId, block);
        CustomBlocksMod.BLOCK_TEXTURES.put(blockId, textureFile);

        CustomBlocksMod.LOGGER.info("[CustomBlocks] Client registered block '{}'", blockId);

        // 3. Regenerate the resource pack (adds this block's assets)
        ResourcePackGenerator.generate();

        // 4. Inject pack entry if not already present
        injectPackIfNeeded(client);

        // 5. Reload resources — textures and models will load instantly
        client.reloadResources().thenRun(() ->
                CustomBlocksMod.LOGGER.info(
                        "[CustomBlocks] Resources reloaded — '{}' is live!", blockId)
        );
    }

    private static void injectPackIfNeeded(MinecraftClient client) {
        if (!client.options.resourcePacks.contains(PACK_ENTRY)) {
            client.options.resourcePacks.add(PACK_ENTRY);
            client.options.write();
        }
    }
}
