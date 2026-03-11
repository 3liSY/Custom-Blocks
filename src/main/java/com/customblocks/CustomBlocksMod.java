package com.customblocks;

import com.customblocks.block.SlotBlock;
import com.customblocks.command.CustomBlockCommand;
import com.customblocks.network.FullSyncPayload;
import com.customblocks.network.SlotUpdatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CustomBlocksMod implements ModInitializer {

    public static final String MOD_ID = "customblocks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final SlotBlock[]      SLOT_BLOCKS = new SlotBlock[SlotManager.MAX_SLOTS];
    public static final SlotBlock.SlotItem[] SLOT_ITEMS = new SlotBlock.SlotItem[SlotManager.MAX_SLOTS];

    public static final RegistryKey<net.minecraft.item.ItemGroup> CUSTOM_BLOCKS_TAB =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(MOD_ID, "blocks"));

    @Override
    public void onInitialize() {

        // Register 64 slot blocks — ALWAYS the same 64, no runtime changes = no registry mismatch
        for (int i = 0; i < SlotManager.MAX_SLOTS; i++) {
            final int idx = i;

            // Dynamic luminance — reads SlotManager at runtime, no restart needed
            AbstractBlock.Settings settings = AbstractBlock.Settings.create()
                    .strength(1.5f, 6.0f)
                    .luminance(state -> {
                        SlotManager.SlotData d = SlotManager.getBySlot("slot_" + idx);
                        return d != null ? d.lightLevel : 0;
                    });

            SlotBlock       block = new SlotBlock(i, settings);
            SlotBlock.SlotItem item  = new SlotBlock.SlotItem(block, new Item.Settings());
            Identifier      id    = Identifier.of(MOD_ID, "slot_" + i);

            Registry.register(Registries.BLOCK, id, block);
            Registry.register(Registries.ITEM, id, item);
            SLOT_BLOCKS[i] = block;
            SLOT_ITEMS[i]  = item;
        }

        // Network
        PayloadTypeRegistry.playS2C().register(FullSyncPayload.ID, FullSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SlotUpdatePayload.ID, SlotUpdatePayload.CODEC);

        // Creative tab
        Registry.register(Registries.ITEM_GROUP, CUSTOM_BLOCKS_TAB,
                FabricItemGroup.builder()
                        .displayName(Text.literal("Custom Blocks"))
                        .icon(() -> {
                            for (SlotManager.SlotData d : SlotManager.allSlots())
                                return new ItemStack(SLOT_ITEMS[d.index]);
                            return new ItemStack(Items.BOOKSHELF);
                        })
                        .entries((ctx, entries) -> {
                            for (SlotManager.SlotData d : SlotManager.allSlots())
                                entries.add(SLOT_ITEMS[d.index]);
                        })
                        .build()
        );

        // Survival block drop
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (!(state.getBlock() instanceof SlotBlock)) return;
            if (player.isCreative()) return;
            world.spawnEntity(new ItemEntity(world,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(state.getBlock())));
        });

        // On join: send all metadata first, then each texture separately (avoids 2MB packet limit)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            List<FullSyncPayload.SlotEntry> meta = new ArrayList<>();
            for (SlotManager.SlotData d : SlotManager.allSlots()) {
                meta.add(new FullSyncPayload.SlotEntry(
                        d.index, d.customId, d.displayName, null,
                        d.lightLevel, d.hardness, d.soundType));
            }
            ServerPlayNetworking.send(handler.player,
                    new FullSyncPayload(meta, SlotManager.getTabIconTexture()));

            for (SlotManager.SlotData d : SlotManager.allSlots()) {
                if (d.texture != null && d.texture.length > 0) {
                    ServerPlayNetworking.send(handler.player,
                            new SlotUpdatePayload("add", d.index, d.customId, d.displayName,
                                    d.texture, d.lightLevel, d.hardness, d.soundType));
                }
            }
        });

        CustomBlockCommand.register();
        SlotManager.loadAll();

        LOGGER.info("[CustomBlocks] Initialized. {} slot(s) loaded.", SlotManager.usedSlots());
    }

    public static void broadcastUpdate(MinecraftServer server, SlotUpdatePayload payload) {
        for (var player : server.getPlayerManager().getPlayerList())
            ServerPlayNetworking.send(player, payload);
    }
}
