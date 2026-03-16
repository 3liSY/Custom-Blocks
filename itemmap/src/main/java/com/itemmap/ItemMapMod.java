package com.itemmap;

import com.itemmap.command.ItemMapCommand;
import com.itemmap.item.ItemMapItem;
import com.itemmap.manager.FrameData;
import com.itemmap.manager.FrameManager;
import com.itemmap.network.FrameSyncPayload;
import com.itemmap.network.FrameUpdatePayload;
import com.itemmap.network.ImagePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ItemMapMod implements ModInitializer {

    public static final String MOD_ID = "itemmap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // The single ItemMapItem instance used for ALL map stacks
    public static ItemMapItem ITEM_MAP_ITEM;

    public static final RegistryKey<ItemGroup> ITEM_MAP_TAB =
        RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(MOD_ID, "maps"));

    // Batch image delivery
    private static final Map<UUID, ConcurrentLinkedQueue<ImagePayload>> PENDING_IMAGES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SEND_DELAY = new ConcurrentHashMap<>();
    private static final int DELAY_TICKS = 60;
    private static final int BATCH_SIZE  = 3;

    @Override
    public void onInitialize() {

        // Register one single ItemMapItem
        ITEM_MAP_ITEM = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "item_map"),
            new ItemMapItem(new Item.Settings().maxCount(1))
        );

        // Register creative tab — one flat + one 3D entry per vanilla item
        Registry.register(Registries.ITEM_GROUP, ITEM_MAP_TAB,
            FabricItemGroup.builder()
                .displayName(Text.literal("Item Maps"))
                .icon(() -> ItemMapItem.create3D(Items.DIAMOND))
                .entries((ctx, entries) -> {
                    List<Item> allItems = new ArrayList<>(Registries.ITEM.stream().toList());
                    allItems.sort(Comparator.comparing(i ->
                        Registries.ITEM.getId(i).toString()));
                    for (Item item : allItems) {
                        if (item == Items.AIR) continue;
                        if (item == ITEM_MAP_ITEM) continue;
                        entries.add(ItemMapItem.createFlat(item));
                        entries.add(ItemMapItem.create3D(item));
                    }
                })
                .build()
        );

        // Register network channels
        PayloadTypeRegistry.playS2C().register(FrameSyncPayload.ID,   FrameSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FrameUpdatePayload.ID, FrameUpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ImagePayload.ID,       ImagePayload.CODEC);

        // On join: sync frame data + queue images
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            List<FrameSyncPayload.FrameEntry> entries = new ArrayList<>();
            for (FrameData d : FrameManager.all())
                entries.add(FrameSyncPayload.fromData(d));
            ServerPlayNetworking.send(player, new FrameSyncPayload(entries));

            ConcurrentLinkedQueue<ImagePayload> queue = new ConcurrentLinkedQueue<>();
            for (String imgId : FrameManager.allImageIds()) {
                byte[] png = FrameManager.getImage(imgId);
                if (png != null && png.length > 0)
                    queue.add(new ImagePayload(imgId, png));
            }
            PENDING_IMAGES.put(player.getUuid(), queue);
            SEND_DELAY.put(player.getUuid(), DELAY_TICKS);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUuid();
            PENDING_IMAGES.remove(uuid);
            SEND_DELAY.remove(uuid);
        });

        // Drip-feed images each tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();
                Integer delay = SEND_DELAY.get(uuid);
                if (delay == null) continue;
                if (delay > 0) { SEND_DELAY.put(uuid, delay - 1); continue; }
                ConcurrentLinkedQueue<ImagePayload> queue = PENDING_IMAGES.get(uuid);
                if (queue == null || queue.isEmpty()) {
                    PENDING_IMAGES.remove(uuid);
                    SEND_DELAY.remove(uuid);
                    continue;
                }
                int sent = 0;
                while (sent < BATCH_SIZE) {
                    ImagePayload pkt = queue.poll();
                    if (pkt == null) break;
                    ServerPlayNetworking.send(player, pkt);
                    sent++;
                }
            }
        });


                ItemMapCommand.register();
        FrameManager.loadAll();

        LOGGER.info("[ItemMap] Initialized.");
    }

    // ── Broadcast helpers ─────────────────────────────────────────────────────

    public static void broadcastFrameUpdate(MinecraftServer server, FrameData data) {
        FrameUpdatePayload pkt = toUpdatePayload("update", data);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            ServerPlayNetworking.send(p, pkt);
    }

    public static void broadcastFrameRemove(MinecraftServer server, long entityId) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            ServerPlayNetworking.send(p, new FrameUpdatePayload(
                "remove", entityId, "FLAT_2D", 2f, 1f, 0f, false, null, 0, null, false));
    }

    public static void broadcastImage(MinecraftServer server, String imageId, byte[] png) {
        ImagePayload pkt = new ImagePayload(imageId, png);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            ServerPlayNetworking.send(p, pkt);
    }

    public static void queueImagesForPlayer(ServerPlayerEntity player,
            ConcurrentLinkedQueue<ImagePayload> queue) {
        PENDING_IMAGES.put(player.getUuid(), queue);
        SEND_DELAY.put(player.getUuid(), 20);
    }

    public static FrameUpdatePayload toUpdatePayload(String action, FrameData d) {
        return new FrameUpdatePayload(
            action, d.entityId, d.mode.name(), d.spinSpeed, d.scale,
            d.padPct, d.glowing, d.label, d.bgColor, d.customImageId, d.invisible);
    }
}
