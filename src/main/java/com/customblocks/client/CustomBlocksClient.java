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
        if (!CustomBlocksMod.CUSTOM_BLOCKS.isEmpty()) {
            ResourcePackGenerator.generate();
        }

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (!CustomBlocksMod.CUSTOM_BLOCKS.isEmpty()) {
                injectPackIfNeeded(client);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(CustomBlockSyncPayload.ID,
            (payload, context) -> {
                String blockId     = payload.blockId();
                String displayName = payload.displayName();
                byte[] texture     = payload.textureData();
                context.client().execute(() -> {
                    try {
                        handleNewBlock(context.client(), blockId, displayName, texture);
                    } catch (Exception e) {
                        CustomBlocksMod.LOGGER.error("[CustomBlocks] Sync error for '{}'", blockId, e);
                    }
                });
            }
        );
    }

    private static void handleNewBlock(MinecraftClient client, String blockId,
                                        String displayName, byte[] textureBytes) throws IOException {
        if (CustomBlocksMod.CUSTOM_BLOCKS.containsKey(blockId)) return;

        File blockFolder = new File("config/customblocks/" + blockId);
        blockFolder.mkdirs();
        File textureFile = new File(blockFolder, "texture.png");
        Files.write(textureFile.toPath(), textureBytes);
        Files.writeString(new File(blockFolder, "name.txt").toPath(), displayName);

        Identifier id = Identifier.of(CustomBlocksMod.MOD_ID, blockId);

        SimpleRegistry<net.minecraft.block.Block> blockReg =
            (SimpleRegistry<net.minecraft.block.Block>) Registries.BLOCK;
        SimpleRegistry<Item> itemReg = (SimpleRegistry<Item>) Registries.ITEM;

        RegistryUtils.unfreeze(blockReg);
        CustomBlock block = new CustomBlock(displayName, AbstractBlock.Settings.create().strength(1.5f, 6.0f));
        Registry.register(Registries.BLOCK, id, block);
        RegistryUtils.freeze(blockReg);

        RegistryUtils.unfreeze(itemReg);
        BlockItem blockItem = new BlockItem(block, new Item.Settings());
        Registry.register(Registries.ITEM, id, blockItem);
        RegistryUtils.freeze(itemReg);

        CustomBlocksMod.CUSTOM_BLOCKS.put(blockId, block);
        CustomBlocksMod.BLOCK_TEXTURES.put(blockId, textureFile);

        ResourcePackGenerator.generate();
        injectPackIfNeeded(client);

        client.reloadResources().thenRun(() ->
            CustomBlocksMod.LOGGER.info("[CustomBlocks] '{}' is live!", blockId));
    }

    private static void injectPackIfNeeded(MinecraftClient client) {
        if (!client.options.resourcePacks.contains(PACK_ENTRY)) {
            client.options.resourcePacks.add(PACK_ENTRY);
            client.options.write();
        }
    }
}
