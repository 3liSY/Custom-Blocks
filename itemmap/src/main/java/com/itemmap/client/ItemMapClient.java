package com.itemmap.client;

import com.itemmap.ItemMapMod;
import com.itemmap.client.gui.ItemMapScreen;
import com.itemmap.client.renderer.FrameRenderManager;
import com.itemmap.item.ItemMapItem;
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
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class ItemMapClient implements ClientModInitializer {

    private static KeyBinding openGuiKey;

    // Spin speed in degrees per tick for 3D maps (2 = ~1 full rotation per 3 seconds)
    private static final float SPIN_DEG_PER_TICK = 2.0f;

    @Override
    public void onInitializeClient() {

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.itemmap.open_gui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "category.itemmap"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            // Advance global GUI spin angle for creative tab / inventory
            FrameRenderManager.tickGuiSpin();

            // Open GUI via keybind
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null)
                    client.setScreen(new ItemMapScreen(-1));
            }

            // Advance spin angles for ALL item frames that hold 3D ItemMapItems
            if (client.world != null) {
                for (net.minecraft.entity.Entity e : client.world.getEntities()) {
                    if (!(e instanceof ItemFrameEntity frame)) continue;
                    ItemStack held = frame.getHeldItemStack();
                    if (held.isEmpty()) continue;
                    if (!(held.getItem() instanceof ItemMapItem)) continue;
                    if (!ItemMapItem.is3D(held)) continue;

                    // Use per-frame spin speed from FrameData if available
                    FrameData data = FrameManager.get(frame.getId());
                    float speed = data != null ? data.spinSpeed : SPIN_DEG_PER_TICK;
                    FrameRenderManager.advanceSpin(frame.getId(), speed);
                }
            }
        });

        // ── FrameSyncPayload ──────────────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(FrameSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                FrameManager.clearAll();
                for (FrameSyncPayload.FrameEntry e : payload.frames()) {
                    FrameData d = new FrameData(e.entityId());
                    applyEntry(d, e);
                    FrameManager.put(d);
                }
            });
        });

        // ── FrameUpdatePayload ────────────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(FrameUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if ("remove".equals(payload.action())) {
                    FrameManager.remove(payload.entityId());
                    FrameRenderManager.removeSpin(payload.entityId());
                    return;
                }
                if ("open_gui".equals(payload.action())) {
                    long fid = payload.entityId();
                    context.client().execute(() ->
                        context.client().setScreen(new ItemMapScreen(fid)));
                    return;
                }
                FrameData d = FrameManager.has(payload.entityId())
                    ? FrameManager.get(payload.entityId())
                    : new FrameData(payload.entityId());
                try { d.mode = FrameData.DisplayMode.valueOf(payload.mode()); }
                catch (Exception ignored) { d.mode = FrameData.DisplayMode.FLAT_2D; }
                d.spinSpeed     = payload.spinSpeed();
                d.scale         = payload.scale();
                d.padPct        = payload.padPct();
                d.glowing       = payload.glowing();
                d.label         = payload.label();
                d.bgColor       = payload.bgColor();
                d.customImageId = payload.customImageId();
                d.invisible     = payload.invisible();
                FrameManager.put(d);
            });
        });

        // ── ImagePayload: custom texture override ─────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ImagePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.png() != null && payload.png().length > 0) {
                    FrameManager.putImage(payload.imageId(), payload.png());
                    FrameRenderManager.setCustomTexture(payload.imageId(), payload.png());
                    ItemMapMod.LOGGER.info("[ItemMap] Received image '{}' ({} KB)",
                        payload.imageId(), payload.png().length / 1024);
                }
            });
        });
    }

    private static void applyEntry(FrameData d, FrameSyncPayload.FrameEntry e) {
        try { d.mode = FrameData.DisplayMode.valueOf(e.mode()); }
        catch (Exception ignored) { d.mode = FrameData.DisplayMode.FLAT_2D; }
        d.spinSpeed     = e.spinSpeed();
        d.scale         = e.scale();
        d.padPct        = e.padPct();
        d.glowing       = e.glowing();
        d.label         = e.label();
        d.bgColor       = e.bgColor();
        d.customImageId = e.customImageId();
        d.invisible     = e.invisible();
    }
}
