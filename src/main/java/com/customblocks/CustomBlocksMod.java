package com.customblocks;

import com.customblocks.block.BlockConfig;
import com.customblocks.block.CustomBlock;
import com.customblocks.command.CustomBlockCommand;
import com.customblocks.network.CustomBlockSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.text.Text;
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
    public static final Map<String, File> BLOCK_TEXTURES = new LinkedHashMap<>();

    public static final String TAB_ICON_ID = "tab_icon";
    public static CustomBlock TAB_ICON_BLOCK = null;

    public static final RegistryKey<net.minecraft.item.ItemGroup> CUSTOM_BLOCKS_TAB =
        RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(MOD_ID, "blocks"));

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(CustomBlockSyncPayload.ID, CustomBlockSyncPayload.CODEC);
        CustomBlockCommand.register();

        Registry.register(Registries.ITEM_GROUP, CUSTOM_BLOCKS_TAB,
            FabricItemGroup.builder()
                .displayName(Text.literal("Custom Blocks"))
                .icon(() -> {
                    if (TAB_ICON_BLOCK != null)
                        return new ItemStack(TAB_ICON_BLOCK);
                    if (!CUSTOM_BLOCKS.isEmpty())
                        return new ItemStack(CUSTOM_BLOCKS.values().iterator().next());
                    return new ItemStack(Items.BOOKSHELF);
                })
                .entries((context, entries) -> {
                    for (CustomBlock block : CUSTOM_BLOCKS.values()) entries.add(block);
                })
                .build()
        );

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(state.getBlock() instanceof CustomBlock)) return;
            if (player.isCreative()) return;
            world.spawnEntity(new ItemEntity(world,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(state.getBlock())));
        });

        loadBlocksFromConfig();
        LOGGER.info("[CustomBlocks] Initialized. {} block(s) loaded.", CUSTOM_BLOCKS.size());
    }

    public static void loadBlocksFromConfig() {
        File configDir = new File("config/customblocks");
        if (!configDir.exists()) { configDir.mkdirs(); return; }
        File[] folders = configDir.listFiles(File::isDirectory);
        if (folders == null) return;

        for (File folder : folders) {
            String blockId = folder.getName().toLowerCase().replaceAll("[^a-z0-9_]", "_");
            if (blockId.isEmpty() || CUSTOM_BLOCKS.containsKey(blockId)) continue;
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

            if (blockId.equals(TAB_ICON_ID)) {
                registerTabIconBlock(folder, textureFile);
                continue;
            }

            registerBlockInternal(blockId, displayName, folder, textureFile, false);
        }
    }

    public static void registerTabIconBlock(File blockFolder, File textureFile) {
        try {
            Identifier id = Identifier.of(MOD_ID, TAB_ICON_ID);
            CustomBlock block = new CustomBlock("Tab Icon", AbstractBlock.Settings.create());
            BlockItem blockItem = new CustomBlock.CustomBlockItem(block, new Item.Settings());
            Registry.register(Registries.BLOCK, id, block);
            Registry.register(Registries.ITEM, id, blockItem);
            TAB_ICON_BLOCK = block;
            BLOCK_TEXTURES.put(TAB_ICON_ID, textureFile);
            LOGGER.info("[CustomBlocks] Tab icon loaded.");
        } catch (Exception e) {
            LOGGER.error("[CustomBlocks] Failed to register tab icon block", e);
        }
    }

    public static boolean registerBlockDynamic(String blockId, String displayName,
                                                File blockFolder, File textureFile) {
        return registerBlockInternal(blockId, displayName, blockFolder, textureFile, true);
    }

    private static boolean registerBlockInternal(String blockId, String displayName,
                                                   File blockFolder, File textureFile, boolean dynamic) {
        try {
            Identifier id = Identifier.of(MOD_ID, blockId);

            if (dynamic) {
                SimpleRegistry<net.minecraft.block.Block> blockReg =
                    (SimpleRegistry<net.minecraft.block.Block>) Registries.BLOCK;
                SimpleRegistry<Item> itemReg = (SimpleRegistry<Item>) Registries.ITEM;

                RegistryUtils.unfreeze(blockReg);
                BlockConfig config = BlockConfig.load(blockFolder);
                CustomBlock block = new CustomBlock(displayName, config.applyTo(AbstractBlock.Settings.create()));
                Registry.register(Registries.BLOCK, id, block);
                RegistryUtils.freeze(blockReg);

                RegistryUtils.unfreeze(itemReg);
                CustomBlock.CustomBlockItem blockItem = new CustomBlock.CustomBlockItem(block, new Item.Settings());
                Registry.register(Registries.ITEM, id, blockItem);
                RegistryUtils.freeze(itemReg);

                CUSTOM_BLOCKS.put(blockId, block);
                BLOCK_TEXTURES.put(blockId, textureFile);
            } else {
                BlockConfig config = BlockConfig.load(blockFolder);
                CustomBlock block = new CustomBlock(displayName, config.applyTo(AbstractBlock.Settings.create()));
                CustomBlock.CustomBlockItem blockItem = new CustomBlock.CustomBlockItem(block, new Item.Settings());
                Registry.register(Registries.BLOCK, id, block);
                Registry.register(Registries.ITEM, id, blockItem);
                CUSTOM_BLOCKS.put(blockId, block);
                BLOCK_TEXTURES.put(blockId, textureFile);
            }

            LOGGER.info("[CustomBlocks] Registered '{}' ('{}')", blockId, displayName);
            return true;
        } catch (Exception e) {
            LOGGER.error("[CustomBlocks] Failed to register block '{}'", blockId, e);
            return false;
        }
    }
}
