package com.customblocks.client;

import com.customblocks.client.gui.CustomBlocksScreen;
import com.customblocks.client.texture.TextureCache;
import com.customblocks.network.FullSyncPayload;
import com.customblocks.network.SlotUpdatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class CustomBlocksClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("CustomBlocks/Client");

    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {

        // ── Key binding: F6 opens the B-Screen ───────────────────────────────
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.customblocks.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "category.customblocks"
        ));

        // ── Packet handlers ───────────────────────────────────────────────────

        // Full sync on join
        ClientPlayNetworking.registerGlobalReceiver(FullSyncPayload.ID, (payload, context) -> {
            ClientSlotData.clear();
            TextureCache.clear();
            for (FullSyncPayload.SlotEntry e : payload.slots()) {
                ClientSlotData d = new ClientSlotData(e.index(), e.customId(), e.displayName(),
                        e.lightLevel(), e.hardness(), e.soundType());
                ClientSlotData.put(d);
            }
            // Tab icon texture
            if (payload.tabIconTexture() != null && payload.tabIconTexture().length > 0) {
                TextureCache.upload("_tab_icon_", payload.tabIconTexture());
            }
            LOGGER.info("[CB/Client] Synced {} block metadata.", payload.slots().size());
        });

        // Individual slot update (texture drip-feed or real-time changes)
        ClientPlayNetworking.registerGlobalReceiver(SlotUpdatePayload.ID, (payload, context) -> {
            String id = payload.customId();
            switch (payload.action()) {
                case "add", "retexture" -> {
                    ClientSlotData existing = ClientSlotData.getById(id);
                    if (existing == null) {
                        ClientSlotData d = new ClientSlotData(payload.slotIndex(), id,
                                payload.displayName() != null ? payload.displayName() : id,
                                payload.lightLevel(), payload.hardness(), payload.soundType());
                        ClientSlotData.put(d);
                    } else {
                        if (payload.displayName() != null) existing.displayName = payload.displayName();
                        existing.lightLevel = payload.lightLevel();
                        existing.hardness   = payload.hardness();
                        existing.soundType  = payload.soundType();
                    }
                    if (payload.texture() != null && payload.texture().length > 0) {
                        TextureCache.invalidate(id);
                        TextureCache.upload(id, payload.texture());
                    }
                }
                case "delete" -> {
                    ClientSlotData.remove(id);
                    TextureCache.invalidate(id);
                }
                case "rename" -> {
                    ClientSlotData d = ClientSlotData.getById(id);
                    if (d != null && payload.displayName() != null) d.displayName = payload.displayName();
                }
                case "update" -> {
                    ClientSlotData d = ClientSlotData.getById(id);
                    if (d != null) {
                        d.lightLevel = payload.lightLevel();
                        d.hardness   = payload.hardness();
                        d.soundType  = payload.soundType();
                    }
                    if (payload.texture() != null && payload.texture().length > 0) {
                        TextureCache.invalidate(id);
                        TextureCache.upload(id, payload.texture());
                    }
                }
            }
        });

        // ── Disconnect: clear client state ───────────────────────────────────
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientSlotData.clear();
            TextureCache.clear();
        });

        // ── Tick: open GUI on key press ───────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new CustomBlocksScreen());
                }
            }
        });

        // ── Drag & drop: handle files dropped onto the window ─────────────────
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        // GLFW drop callback — set after window is created
        // We defer via a tick to ensure the window handle is ready
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.getWindow() != null && !dropCallbackSet) {
                setupDropCallback(client.getWindow().getHandle());
                dropCallbackSet = true;
            }
        });

        LOGGER.info("[CustomBlocks v10 Client] Initialized. Press F6 to open GUI.");
    }

    private static boolean dropCallbackSet = false;

    private static void setupDropCallback(long windowHandle) {
        GLFW.glfwSetDropCallback(windowHandle, (window, count, names) -> {
            if (count <= 0) return;
            String path = org.lwjgl.glfw.GLFWDropCallback.getName(names, 0);
            if (path == null) return;

            Thread.ofVirtual().start(() -> {
                try {
                    byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
                    // Try to decode as image
                    try {
                        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                        if (img == null) return; // not an image
                        // Resize to 16×16
                        java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        java.awt.Graphics2D g = scaled.createGraphics();
                        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        g.drawImage(img, 0, 0, 16, 16, null); g.dispose();
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        javax.imageio.ImageIO.write(scaled, "PNG", baos);
                        byte[] pngBytes = baos.toByteArray();

                        net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                            if (client.currentScreen instanceof CustomBlocksScreen screen) {
                                // Open image editor with the dropped image
                                client.setScreen(new com.customblocks.client.gui.ImageEditorScreen("create", null, screen) {{
                                    setImageBytes(pngBytes);
                                }});
                            } else if (client.currentScreen instanceof com.customblocks.client.gui.ImageEditorScreen edScreen) {
                                edScreen.setImageBytes(pngBytes);
                            } else {
                                // Not in a CB screen — open editor anyway
                                client.setScreen(new com.customblocks.client.gui.ImageEditorScreen("create", null, client.currentScreen) {{
                                    setImageBytes(pngBytes);
                                }});
                            }
                        });
                    } catch (Exception e) {
                        LOGGER.warn("[CB/Client] Dropped file '{}' is not a valid image: {}", path, e.getMessage());
                    }
                } catch (Exception e) {
                    LOGGER.error("[CB/Client] Failed to read dropped file: {}", e.getMessage());
                }
            });
        });
        LOGGER.info("[CB/Client] Drag-drop enabled.");
    }
}
