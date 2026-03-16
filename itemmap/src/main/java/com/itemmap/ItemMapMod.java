package com.itemmap;

import com.itemmap.command.ItemMapCommand;
import com.itemmap.manager.FrameData;
import com.itemmap.manager.FrameManager;
import com.itemmap.network.FrameSyncPayload;
import com.itemmap.network.FrameUpdatePayload;
import com.itemmap.network.ImagePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ItemMapMod implements ModInitializer {

    public static final String MOD_ID = "itemmap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Batch image delivery — same proven pattern as CustomBlocks
    private static final Map<UUID, ConcurrentLinkedQueue<ImagePayload>> PENDING_IMAGES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SEND_DELAY = new ConcurrentHashMap<>();
    private static final int DELAY_TICKS = 60;  // 3s after join
    private static final int BATCH_SIZE  = 3;   // images per tick (128x128 PNG ~8KB each)

    @Override
    public void onInitialize() {
        // Register network channels
        PayloadTypeRegistry.playS2C().register(FrameSyncPayload.ID,   FrameSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FrameUpdatePayload.ID, FrameUpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ImagePayload.ID,       ImagePayload.CODEC);

        // On join: send full frame metadata + queue images
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;

            // 1) Send all frame metadata immediately
            List<FrameSyncPayload.FrameEntry> entries = new ArrayList<>();
            for (FrameData d : FrameManager.all())
                entries.add(FrameSyncPayload.fromData(d));
            ServerPlayNetworking.send(player, new FrameSyncPayload(entries));

            // 2) Queue all custom images for drip-feed
            ConcurrentLinkedQueue<ImagePayload> queue = new ConcurrentLinkedQueue<>();
            for (String imgId : FrameManager.allImageIds()) {
                byte[] png = FrameManager.getImage(imgId);
                if (png != null && png.length > 0)
                    queue.add(new ImagePayload(imgId, png));
            }
            UUID uuid = player.getUuid();
            PENDING_IMAGES.put(uuid, queue);
            SEND_DELAY.put(uuid, DELAY_TICKS);
        });

        // On disconnect: clean up
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.player.getUuid();
            PENDING_IMAGES.remove(uuid);
            SEND_DELAY.remove(uuid);
        });

        // Tick: drip-feed images
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

        // Right-click item frame with empty hand → open GUI (send open packet via command feedback)
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(entity instanceof ItemFrameEntity frame)) return ActionResult.PASS;
            if (!player.getStackInHand(hand).isEmpty()) return ActionResult.PASS;
            if (frame.getHeldItemStack().isEmpty()) return ActionResult.PASS;
            if (!player.hasPermissionLevel(2)) return ActionResult.PASS;

            // Notify client to open the GUI for this frame
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            FrameData data = FrameManager.getOrCreate(frame.getId());
            FrameUpdatePayload pkt = toUpdatePayload("open_gui", data);
            ServerPlayNetworking.send(serverPlayer, pkt);
            return ActionResult.SUCCESS;
        });

        ItemMapCommand.register();
        FrameManager.loadAll();

        LOGGER.info("[ItemMap] Initialized. {} frame(s) loaded.", FrameManager.all().size());
    }

    // ── Broadcast helpers ────────────────────────────────────────────────────

    public static void broadcastFrameUpdate(MinecraftServer server, FrameData data) {
        FrameUpdatePayload pkt = toUpdatePayload("update", data);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())
            ServerPlayNetworking.send(player, pkt);
    }

    public static void broadcastFrameRemove(MinecraftServer server, long entityId) {
        FrameUpdatePayload pkt = new FrameUpdatePayload(
            "remove", entityId, "FLAT_2D", 2f, 1f, 0f, false, null, 0, null, false);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())
            ServerPlayNetworking.send(player, pkt);
    }

    public static void broadcastImage(MinecraftServer server, String imageId, byte[] png) {
        ImagePayload pkt = new ImagePayload(imageId, png);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())
            ServerPlayNetworking.send(player, pkt);
    }

    /** Queue images for one player (used by /im reload). */
    public static void queueImagesForPlayer(ServerPlayerEntity player,
            ConcurrentLinkedQueue<ImagePayload> queue) {
        UUID uuid = player.getUuid();
        PENDING_IMAGES.put(uuid, queue);
        SEND_DELAY.put(uuid, 20);
    }

    private static FrameUpdatePayload toUpdatePayload(String action, FrameData d) {
        return new FrameUpdatePayload(
            action, d.entityId, d.mode.name(), d.spinSpeed, d.scale,
            d.padPct, d.glowing, d.label, d.bgColor, d.customImageId, d.invisible);
    }
}
