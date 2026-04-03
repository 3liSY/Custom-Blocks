package com.customblocks;

import com.customblocks.block.SlotBlock;
import com.customblocks.command.CustomBlockCommand;
import com.customblocks.item.ColorSquareItem;
import com.customblocks.network.FullSyncPayload;
import com.customblocks.network.ImageEditPayload;
import com.customblocks.network.SlotUpdatePayload;
import com.customblocks.util.ImageProcessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CustomBlocksMod implements ModInitializer {

    public static final String MOD_ID = "customblocks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final SlotBlock[]          SLOT_BLOCKS = new SlotBlock[SlotManager.MAX_SLOTS];
    public static final SlotBlock.SlotItem[] SLOT_ITEMS  = new SlotBlock.SlotItem[SlotManager.MAX_SLOTS];

    // Drip-feed texture sync
    private static final Map<UUID, ConcurrentLinkedQueue<SlotUpdatePayload>> PENDING =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SEND_DELAY = new ConcurrentHashMap<>();
    private static final int DELAY_TICKS = 60;
    private static final int BATCH_SIZE  = 4;

    public static final RegistryKey<net.minecraft.item.ItemGroup> CUSTOM_BLOCKS_TAB =
            RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(MOD_ID, "blocks"));

    @Override
    public void onInitialize() {

        // ── Register 512 slot blocks ─────────────────────────────────────────
        for (int i = 0; i < SlotManager.MAX_SLOTS; i++) {
            final int idx = i;
            AbstractBlock.Settings settings = AbstractBlock.Settings.create()
                    .strength(1.5f, 6.0f)
                    .luminance(state -> {
                        SlotManager.SlotData d = SlotManager.getBySlot("slot_" + idx);
                        return d != null ? d.lightLevel : 0;
                    });

            SlotBlock block = new SlotBlock(i, settings);
            Identifier id   = Identifier.of(MOD_ID, "slot_" + i);
            SlotBlock.SlotItem item = new SlotBlock.SlotItem(block, new Item.Settings());

            Registry.register(Registries.BLOCK, id, block);
            Registry.register(Registries.ITEM,  id, item);
            SLOT_BLOCKS[i] = block;
            SLOT_ITEMS[i]  = item;
        }

        // ── Color square items ────────────────────────────────────────────────
        for (String[] sq : new String[][]{{"black","Black"},{"yellow","Yellow"},{"green","Green"}}) {
            Registry.register(Registries.ITEM, Identifier.of(MOD_ID, sq[0] + "_square"),
                    new ColorSquareItem(sq[0], sq[1], new Item.Settings().maxCount(1)));
        }

        // ── Network ───────────────────────────────────────────────────────────
        PayloadTypeRegistry.playS2C().register(FullSyncPayload.ID,    FullSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SlotUpdatePayload.ID,  SlotUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ImageEditPayload.ID,   ImageEditPayload.CODEC);

        // Handle C2S image edit packets
        ServerPlayNetworking.registerGlobalReceiver(ImageEditPayload.ID, (payload, context) ->
                context.server().execute(() -> handleImageEdit(payload, context.player(), context.server()))
        );

        // ── Creative tab ──────────────────────────────────────────────────────
        Registry.register(Registries.ITEM_GROUP, CUSTOM_BLOCKS_TAB,
                FabricItemGroup.builder()
                        .displayName(Text.literal("Custom Blocks v10"))
                        .icon(() -> {
                            for (SlotManager.SlotData d : SlotManager.allSlots())
                                return new ItemStack(SLOT_ITEMS[d.index]);
                            return new ItemStack(Items.BOOKSHELF);
                        })
                        .entries((ctx, entries) -> {
                            for (SlotManager.SlotData d : SlotManager.allSlots())
                                entries.add(SLOT_ITEMS[d.index]);
                            for (String col : new String[]{"black","yellow","green"}) {
                                Item sq = Registries.ITEM.get(Identifier.of(MOD_ID, col + "_square"));
                                if (sq != null && sq != Items.AIR) entries.add(sq);
                            }
                        })
                        .build()
        );

        // ── Block break drop ──────────────────────────────────────────────────
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (!(state.getBlock() instanceof SlotBlock)) return;
            if (player.isCreative()) return;
            world.spawnEntity(new ItemEntity(world,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(state.getBlock())));
        });

        // ── Player join: metadata immediately, textures drip-feed ────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Send metadata
            List<FullSyncPayload.SlotEntry> meta = new ArrayList<>();
            for (SlotManager.SlotData d : SlotManager.allSlots())
                meta.add(new FullSyncPayload.SlotEntry(d.index, d.customId, d.displayName, null,
                        d.lightLevel, d.hardness, d.soundType));
            ServerPlayNetworking.send(handler.player,
                    new FullSyncPayload(meta, SlotManager.getTabIconTexture()));

            // Queue texture payloads
            ConcurrentLinkedQueue<SlotUpdatePayload> queue = new ConcurrentLinkedQueue<>();
            SlotManager.allSlots().stream()
                    .filter(d -> d.texture != null && d.texture.length > 0)
                    .sorted(Comparator.comparingInt(d -> d.index))
                    .forEach(d -> queue.add(new SlotUpdatePayload(
                            "add", d.index, d.customId, d.displayName, d.texture,
                            d.lightLevel, d.hardness, d.soundType)));
            UUID uuid = handler.player.getUuid();
            PENDING.put(uuid, queue);
            SEND_DELAY.put(uuid, DELAY_TICKS);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUuid();
            PENDING.remove(uuid); SEND_DELAY.remove(uuid);
        });

        // ── Tick: drip-feed textures ──────────────────────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();
                Integer delay = SEND_DELAY.get(uuid);
                if (delay == null) continue;
                if (delay > 0) { SEND_DELAY.put(uuid, delay - 1); continue; }
                ConcurrentLinkedQueue<SlotUpdatePayload> queue = PENDING.get(uuid);
                if (queue == null) { SEND_DELAY.remove(uuid); continue; }
                for (int sent = 0; sent < BATCH_SIZE; sent++) {
                    SlotUpdatePayload pkt = queue.poll();
                    if (pkt == null) break;
                    ServerPlayNetworking.send(player, pkt);
                }
                if (queue.isEmpty()) { PENDING.remove(uuid); SEND_DELAY.remove(uuid); }
            }
        });

        // ── Startup ───────────────────────────────────────────────────────────
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            PackHttpServer.start();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PackHttpServer.stop();
        });

        CustomBlockCommand.register();
        SlotManager.loadAll();
        PermissionManager.load();
        TemplateManager.load();

        try {
            ResourcePackExporter.exportCurrentPackZip(new File("config/customblocks"));
        } catch (Exception e) {
            LOGGER.error("[CustomBlocks] Failed to generate startup pack", e);
        }

        LOGGER.info("[CustomBlocks v10] Initialized. {} slot(s) loaded.", SlotManager.usedSlots());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Packet handler
    // ─────────────────────────────────────────────────────────────────────────

    private static void handleImageEdit(ImageEditPayload payload, ServerPlayerEntity player, MinecraftServer server) {
        if (!PermissionManager.canEdit(player)) {
            player.sendMessage(Text.literal("§c[CustomBlocks] No permission to edit blocks."), false);
            return;
        }
        if (payload.texture() == null || payload.texture().length == 0) {
            player.sendMessage(Text.literal("§c[CustomBlocks] No image data."), false);
            return;
        }

        byte[] tex;
        tex = null;
        catch (Exception e) {
            player.sendMessage(Text.literal("§c[CustomBlocks] Invalid image: " + e.getMessage()), false);
            return;
        }

        switch (payload.action()) {
            case "create" -> {
                String id   = sanitizeId(payload.customId());
                String name = payload.displayName() != null ? payload.displayName().trim() : id;
                if (id.isEmpty()) { player.sendMessage(Text.literal("§c[CustomBlocks] Missing block ID."), false); return; }
                if (SlotManager.hasId(id)) { player.sendMessage(Text.literal("§c[CustomBlocks] ID '" + id + "' already exists."), false); return; }
                SlotManager.SlotData data = SlotManager.assign(id, name, tex);
                if (data == null) { player.sendMessage(Text.literal("§c[CustomBlocks] No free slots."), false); return; }
                finishCreate(server, player, data, tex);
            }
            case "retexture" -> {
                String id = payload.customId();
                SlotManager.SlotData data = SlotManager.getById(id);
                if (data == null) { player.sendMessage(Text.literal("§c[CustomBlocks] Not found: " + id), false); return; }
                SlotManager.updateTexture(id, tex);
                SlotManager.saveAll();
                regenPack(server);
                broadcastUpdate(server, new SlotUpdatePayload("retexture", data.index, id, null, tex, data.lightLevel, data.hardness, data.soundType));
                player.sendMessage(Text.literal("§a[CustomBlocks] Texture updated for '" + id + "'."), false);
            }
            case "face" -> {
                String id   = payload.customId();
                String face = payload.extra();
                SlotManager.SlotData data = SlotManager.getById(id);
                if (data == null || face == null) return;
                SlotManager.setFaceTexture(id, face, tex);
                SlotManager.saveAll();
                regenPack(server);
                broadcastUpdate(server, new SlotUpdatePayload("update", data.index, id, null, null, data.lightLevel, data.hardness, data.soundType));
                player.sendMessage(Text.literal("§a[CustomBlocks] Face '" + face + "' set on '" + id + "'."), false);
            }
            case "anim_frame" -> {
                String id = payload.customId();
                SlotManager.SlotData data = SlotManager.getById(id);
                if (data == null) return;
                data.animFrames.add(tex);
                SlotManager.saveAll();
                regenPack(server);
                player.sendMessage(Text.literal("§a[CustomBlocks] Anim frame " + data.animFrames.size() + " added to '" + id + "'."), false);
            }
            case "rand_variant" -> {
                String id = payload.customId();
                if (!SlotManager.addRandomVariant(id, tex)) {
                    player.sendMessage(Text.literal("§c[CustomBlocks] Max 8 variants per block."), false);
                    return;
                }
                SlotManager.saveAll();
                regenPack(server);
                player.sendMessage(Text.literal("§a[CustomBlocks] Random variant added."), false);
            }
            case "sound" -> {
                String id   = payload.customId();
                String type = payload.extra(); // break/place/step
                if (!SlotManager.setCustomSound(id, type, payload.texture())) {
                    player.sendMessage(Text.literal("§c[CustomBlocks] Invalid sound type: " + type), false);
                    return;
                }
                SlotManager.saveAll();
                regenPack(server);
                player.sendMessage(Text.literal("§a[CustomBlocks] Sound '" + type + "' set on '" + id + "'."), false);
            }
        }
    }

    private static void finishCreate(MinecraftServer server, ServerPlayerEntity player,
                                     SlotManager.SlotData data, byte[] tex) {
        SlotManager.saveAll();
        regenPack(server);
        broadcastUpdate(server, new SlotUpdatePayload("add", data.index, data.customId,
                data.displayName, tex, data.lightLevel, data.hardness, data.soundType));
        player.sendMessage(Text.literal("§a[CustomBlocks] Created '" + data.displayName + "' (slot " + data.index + ")."), false);
        player.getInventory().insertStack(new ItemStack(SLOT_ITEMS[data.index]));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Broadcast helpers
    // ─────────────────────────────────────────────────────────────────────────

    public static void broadcastUpdate(MinecraftServer server, SlotUpdatePayload payload) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            ConcurrentLinkedQueue<SlotUpdatePayload> queue = PENDING.get(uuid);
            if (queue != null && !queue.isEmpty()) {
                // Replace matching entry in queue instead of double-sending
                ConcurrentLinkedQueue<SlotUpdatePayload> newQ = new ConcurrentLinkedQueue<>();
                boolean replaced = false;
                for (SlotUpdatePayload q : queue) {
                    if (q.customId() != null && q.customId().equals(payload.customId())) {
                        newQ.add(payload); replaced = true;
                    } else { newQ.add(q); }
                }
                PENDING.put(uuid, newQ);
                if (!replaced) ServerPlayNetworking.send(player, payload);
            } else {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public static void regenPack(MinecraftServer server) {
        server.execute(() -> {
            try {
                ResourcePackExporter.exportCurrentPackZip(new File("config/customblocks"));
            } catch (Exception e) {
                LOGGER.error("[CustomBlocks] Pack regen failed: {}", e.getMessage());
            }
        });
    }

    private static String sanitizeId(String id) {
        if (id == null) return "";
        return id.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }
}
