package com.customblocks;

import com.customblocks.block.BlockConfig;
import com.customblocks.block.CustomBlock;
import com.customblocks.command.CustomBlockCommand;
import com.customblocks.network.CustomBlockSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class CustomBlocksMod implements ModInitializer {

    public static final String MOD_ID = "customblocks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Map<String, CustomBlock> CUSTOM_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, File>        BLOCK_TEXTURES = new LinkedHashMap<>();

    @Override
    public void onInitialize() {
        // Register the S2C packet type
        PayloadTypeRegistry.playS2C().register(
                CustomBlockSyncPayload.ID,
                CustomBlockSyncPayload.CODEC
        );

        // Register commands
        CustomBlockCommand.register();

        // Load blocks that already exist in config
        loadBlocksFromConfig();

        if (!CUSTOM_BLOCKS.isEmpty()) {
            addToCreativeTab();
            registerDrops();
            LOGGER.info("[CustomBlocks] {} block(s) loaded from config.", CUSTOM_BLOCKS.size());
        }
    }

    public static void loadBlocksFromConfig() {
        File configDir = new File("config/customblocks");
        if (!configDir.exists()) {
            configDir.mkdirs();
            return;
        }

        File[] folders = configDir.listFiles(File::isDirectory);
        if (folders == null) return;

        for (File folder : folders) {
            String blockId = folder.getName().toLowerCase().replaceAll("[^a-z0-9_]", "_");
            if (blockId.isEmpty()) continue;
            if (CUSTOM_BLOCKS.containsKey(blockId)) continue; // already registered

            File textureFile = new File(folder, "texture.png");
            if (!textureFile.exists()) continue;

            String displayName = folder.getName();
            File nameFile = new File(folder, "name.txt");
            if (nameFile.exists()) {
                try {
                    String txt = Files.readString(nameFile.toPath()).trim();
                    if (!txt.isEmpty()) displayName = txt;
                } catch (IOException ignored) {}
            }

            registerBlockDynamic(blockId, displayName, folder, textureFile);
        }
    }

    /**
     * Dynamically registers a block and item into the frozen registries.
     * Called both at startup (for existing config) and at runtime (from command).
     */
    public static boolean registerBlockDynamic(String blockId, String displayName,
                                                File blockFolder, File textureFile) {
        try {
            BlockConfig config = BlockConfig.load(blockFolder);
            AbstractBlock.Settings settings = config.applyTo(AbstractBlock.Settings.create());
            CustomBlock block = new CustomBlock(displayName, settings);
            BlockItem blockItem = new BlockItem(block, new Item.Settings());

            SimpleRegistry<net.minecraft.block.Block> blockReg =
                    (SimpleRegistry<net.minecraft.block.Block>) Registries.BLOCK;
            SimpleRegistry<Item> itemReg =
                    (SimpleRegistry<Item>) Registries.ITEM;

            RegistryUtils.unfreeze(blockReg);
            Registry.register(Registries.BLOCK, Identifier.of(MOD_ID, blockId), block);
            RegistryUtils.freeze(blockReg);

            RegistryUtils.unfreeze(itemReg);
            Registry.register(Registries.ITEM, Identifier.of(MOD_ID, blockId), blockItem);
            RegistryUtils.freeze(itemReg);

            CUSTOM_BLOCKS.put(blockId, block);
            BLOCK_TEXTURES.put(blockId, textureFile);

            LOGGER.info("[CustomBlocks] Registered '{}' as '{}'", blockId, displayName);
            return true;

        } catch (Exception e) {
            LOGGER.error("[CustomBlocks] Failed to register block '{}'", blockId, e);
            return false;
        }
    }

    public static void addToCreativeTab() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
            for (CustomBlock block : CUSTOM_BLOCKS.values()) {
                entries.add(block);
            }
        });
    }

    public static void registerDrops() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(state.getBlock() instanceof CustomBlock)) return;
            if (player.isCreative()) return;
            ItemEntity drop = new ItemEntity(
                    world,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    new ItemStack(state.getBlock())
            );
            world.spawnEntity(drop);
        });
    }
}
