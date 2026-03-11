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
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
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

    public static volatile boolean pendingCreativeRefresh = false;

    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.customblocks.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.customblocks"
        ));

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            SlotManager.loadFromClientDir(client.runDirectory);
            ResourcePackGenerator.generate(client);
            injectPackIfNeeded(client);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null)
                    client.setScreen(new CustomBlocksScreen());
            }

            if (pendingCreativeRefresh && client.player != null
                    && client.currentScreen instanceof CreativeInventoryScreen) {
                pendingCreativeRefresh = false;
                client.setScreen(new CreativeInventoryScreen(
                        client.player,
                        client.player.networkHandler.getEnabledFeatures(),
                        false
                ));
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FullSyncPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                SlotManager.clearAll();
                for (FullSyncPayload.SlotEntry e : payload.entries()) {
                    SlotManager.assignAtIndex(e.index(), e.customId(), e.displayName(), null);
                    SlotManager.setProperties(e.customId(), e.lightLevel(), e.hardness(), e.soundType());
                }
                if (payload.tabIconTexture() != null)
                    SlotManager.setTabIconTexture(payload.tabIconTexture());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SlotUpdatePayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                switch (payload.action()) {
                    case "add" -> {
                        if (SlotManager.getById(payload.customId()) != null)
                            SlotManager.updateTexture(payload.customId(), payload.texture());
                        else
                            SlotManager.assignAtIndex(payload.slotIndex(), payload.customId(), payload.displayName(), payload.texture());
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
                    case "tabicon" -> SlotManager.setTabIconTexture(payload.texture());
                    case "setprop" -> SlotManager.setProperties(payload.customId(),
                            payload.lightLevel(), payload.hardness(), payload.soundType());
                }
                SlotManager.saveToClientDir(client.runDirectory);
                ResourcePackGenerator.generate(client);
                injectPackIfNeeded(client);
                scheduleReload(client);
            });
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;
            if (!(client.crosshairTarget instanceof BlockHitResult bhr)) return;

            var state = client.world.getBlockState(bhr.getBlockPos());
            if (!(state.getBlock() instanceof SlotBlock sb)) return;

            SlotManager.SlotData data = SlotManager.getBySlot(sb.getSlotKey());
            if (data == null) return;

            String name = data.displayName;
            int cx = ctx.getScaledWindowWidth() / 2;
            int w  = client.textRenderer.getWidth(name);

            ctx.fill(cx - w / 2 - 5, 38, cx + w / 2 + 5, 52, 0x88000000);
            ctx.drawCenteredTextWithShadow(client.textRenderer, name, cx, 42, 0xFFFFFFFF);
        });
    }

    private static void scheduleReload(MinecraftClient client) {
        if (reloadScheduled.compareAndSet(false, true)) {
            Thread t = new Thread(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                client.execute(() -> {
                    reloadScheduled.set(false);
                    client.reloadResources().thenRun(() ->
                        client.execute(() -> {
                            CustomBlocksMod.LOGGER.info("[CustomBlocks] Resources reloaded.");
                            pendingCreativeRefresh = true;
                        })
                    );
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