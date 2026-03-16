package com.itemmap.client;

import com.itemmap.ItemMapMod;
import com.itemmap.client.gui.ItemMapScreen;
import com.itemmap.client.renderer.FrameRenderManager;
import com.itemmap.manager.FrameData;
import com.itemmap.manager.FrameManager;
import com.itemmap.network.FrameSyncPayload;
import com.itemmap.network.FrameUpdatePayload;
import com.itemmap.network.ImagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class ItemMapClient implements ClientModInitializer {

    private static KeyBinding openGuiKey;

    // The entity ID of the frame whose GUI we should open (set by server packet)
    public static volatile long pendingGuiFrameId = -1;

    @Override
    public void onInitializeClient() {

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.itemmap.open_gui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "category.itemmap"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Open GUI via keybind — shows list of all configured frames
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null)
                    client.setScreen(new ItemMapScreen(-1));
            }
            // Open GUI for specific frame (triggered by right-click)
            if (pendingGuiFrameId >= 0 && client.currentScreen == null) {
                long fid = pendingGuiFrameId;
                pendingGuiFrameId = -1;
                client.setScreen(new ItemMapScreen(fid));
            }
            // Tick spin angles for all SPIN_3D frames
            FrameRenderManager.tickSpins();
        });

        // ── FrameSyncPayload: full sync on join ───────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(FrameSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                // Only wipe client-side data when on a remote dedicated server.
                // On integrated (singleplayer) server the server has its own authoritative
                // data in FrameManager already, and clearAll would wipe it.
                // Since this packet is S2C we trust it — always clear and re-apply.
                // On integrated server FrameManager is shared: we reload from the payload
                // which mirrors the server state, so this is safe.
                FrameManager.clearAll();
                for (FrameSyncPayload.FrameEntry e : payload.frames()) {
                    FrameData d = new FrameData(e.entityId());
                    applyEntry(d, e.mode(), e.spinSpeed(), e.scale(), e.padPct(),
                        e.glowing(), e.label(), e.bgColor(), e.customImageId(), e.invisible());
                    FrameManager.put(d);
                }
                ItemMapMod.LOGGER.info("[ItemMap] Synced {} frame(s) from server.", payload.frames().size());
            });
        });

        // ── FrameUpdatePayload: single frame change ────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(FrameUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if ("remove".equals(payload.action())) {
                    FrameManager.remove(payload.entityId());
                    return;
                }
                if ("open_gui".equals(payload.action())) {
                    pendingGuiFrameId = payload.entityId();
                    return;
                }
                // "update" — create or update
                FrameData d = FrameManager.has(payload.entityId())
                    ? FrameManager.get(payload.entityId())
                    : new FrameData(payload.entityId());
                applyEntry(d, payload.mode(), payload.spinSpeed(), payload.scale(),
                    payload.padPct(), payload.glowing(), payload.label(),
                    payload.bgColor(), payload.customImageId(), payload.invisible());
                FrameManager.put(d);
            });
        });

        // ── ImagePayload: receive custom image ────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ImagePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.png() != null && payload.png().length > 0) {
                    FrameManager.putImage(payload.imageId(), payload.png());
                    // Invalidate cached texture for this image ID
                    FrameRenderManager.invalidateImage(payload.imageId());
                    ItemMapMod.LOGGER.info("[ItemMap] Received image '{}' ({} KB)",
                        payload.imageId(), payload.png().length / 1024);
                }
            });
        });
    }

    private static void applyEntry(FrameData d, String mode, float spinSpeed, float scale,
            float padPct, boolean glowing, String label, int bgColor,
            String customImageId, boolean invisible) {
        try { d.mode = FrameData.DisplayMode.valueOf(mode); }
        catch (Exception ignored) { d.mode = FrameData.DisplayMode.FLAT_2D; }
        d.spinSpeed     = spinSpeed;
        d.scale         = scale;
        d.padPct        = padPct;
        d.glowing       = glowing;
        d.label         = label;
        d.bgColor       = bgColor;
        d.customImageId = customImageId;
        d.invisible     = invisible;
    }
}
