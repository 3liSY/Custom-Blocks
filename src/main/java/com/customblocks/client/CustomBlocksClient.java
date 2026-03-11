package com.customblocks.client;

import com.customblocks.CustomBlocksMod;
import com.customblocks.SlotManager;
import com.customblocks.block.SlotBlock;
import com.customblocks.client.gui.CustomBlocksScreen;
import com.customblocks.client.texture.TextureCache;
import com.customblocks.network.FullSyncPayload;
import com.customblocks.network.SlotUpdatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public class CustomBlocksClient implements ClientModInitializer {

    private static final String PACK_ENTRY = "file/customblocks_generated";
    private static final AtomicBoolean reloadScheduled = new AtomicBoolean(false);

    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {

        // Register B keybind to open GUI
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.customblocks.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.customblocks"
        ));

        // Load & generate on startup
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            SlotManager.loadFromClientDir(client.runDirectory);
            ResourcePackGenerator.generate(client);
            injectPackIfNeeded(client);
        });

        // Keybind tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new CustomBlocksScreen());
                }
            }
        });

        // ── FullSyncPayload: server sends all slot metadata + tab icon on join ──
        ClientPlayNetworking.registerGlobalReceiver(FullSyncPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                // Apply all slot metadata (textures arrive separately via SlotUpdatePayload)
                for (FullSyncPayload.SlotEntry e : payload.entries()) {
                    if (SlotManager.getBySlot("slot_" + e.index()) == null) {
                        SlotManager.assign(e.customId(), e.displayName(), null);
                    }
                    // Update properties even if slot already exists
                    SlotManager.setProperties(e.customId(), e.lightLevel(), e.hardness(), e.soundType());
                }
                if (payload.tabIconTexture() != null) {
                    SlotManager.setTabIconTexture(payload.tabIconTexture());
                }
            });
        });

        // ── SlotUpdatePayload: single-slot changes ──────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(SlotUpdatePayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                switch (payload.action()) {
                    case "add" -> {
                        if (SlotManager.getById(payload.customId()) != null) {
                            SlotManager.updateTexture(payload.customId(), payload.texture());
                        } else {
                            SlotManager.assign(payload.customId(), payload.displayName(), payload.texture());
                        }
                        SlotManager.setProperties(payload.customId(),
                                payload.lightLevel(), payload.hardness(), payload.soundType());
                        TextureCache.invalidate(payload.customId());
                    }
                    case "remove" -> {
                        TextureCache.invalidate(payload.customId());
                        SlotManager.remove(payload.customId());
                    }
                    case "rename"    -> SlotManager.rename(payload.customId(), payload.displayName());
                    case "retexture" -> {
                        SlotManager.updateTexture(payload.customId(), payload.texture());
                        TextureCache.invalidate(payload.customId());
                    }
                    case "tabicon"   -> SlotManager.setTabIconTexture(payload.texture());
                    case "setprop"   -> SlotManager.setProperties(payload.customId(),
                            payload.lightLevel(), payload.hardness(), payload.soundType());
                }
                SlotManager.saveToClientDir(client.runDirectory);
                ResourcePackGenerator.generate(client);
                injectPackIfNeeded(client);
                scheduleReload(client);
            });
        });

        // ── HUD overlay: show block name when looking at a custom block ─────────
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;
            if (!(client.crosshairTarget instanceof BlockHitResult bhr)) return;

            var state = client.world.getBlockState(bhr.getBlockPos());
            if (!(state.getBlock() instanceof SlotBlock sb)) return;

            SlotManager.SlotData data = SlotManager.getBySlot(sb.getSlotKey());
            if (data == null) return;

            int cx = ctx.getScaledWindowWidth() / 2;
            int w  = client.textRenderer.getWidth(data.displayName) + 10;

            // Background pill
            ctx.fill(cx - w / 2 - 2, 40, cx + w / 2 + 2, 54, 0x88000000);
            ctx.drawBorder(cx - w / 2 - 2, 40, w + 4, 14, 0x44FFFFFF);

            // Block name + ID
            ctx.drawCenteredTextWithShadow(client.textRenderer,
                    "§f" + data.displayName + " §7[" + data.customId + "]",
                    cx, 43, 0xFFFFFF);
        });
    }

    /**
     * Debounced resource reload — waits 1s after the last packet, then does one reload.
     */
    private static void scheduleReload(MinecraftClient client) {
        if (reloadScheduled.compareAndSet(false, true)) {
            Thread t = new Thread(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                client.execute(() -> {
                    reloadScheduled.set(false);
                    client.reloadResources().thenRun(() ->
                            CustomBlocksMod.LOGGER.info("[CustomBlocks] Resources reloaded."));
                });
            }, "CustomBlocks-Reload");
            t.setDaemon(true);
            t.start();
        }
    }

    private static void injectPackIfNeeded(MinecraftClient client) {
        if (!client.options.resourcePacks.contains(PACK_ENTRY)) {
            client.options.resourcePacks.add(PACK_ENTRY);
            client.options.write();
        }
    }
}
